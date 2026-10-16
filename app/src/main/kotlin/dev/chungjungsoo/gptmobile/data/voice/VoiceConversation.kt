package dev.chungjungsoo.gptmobile.data.voice

import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoiceConversation(
    private val stt: SpeechToText,
    private val tts: TextToSpeechEngine,
    private val vad: VoiceActivityDetector,
    private val audioFocus: AudioFocusGateway,
    private val headset: HeadsetRouter,
    private val scope: CoroutineScope,
    private val submitTurn: (String) -> Boolean,
    private val cancelTurn: () -> Unit
) {
    private val reducer = VoiceSessionReducer()
    private val sentenceQueue = SpokenSentenceQueue()
    private val commands = Channel<VoiceCommand>(Channel.BUFFERED)
    private val pendingSpeech = ArrayDeque<String>()
    private val speechLock = Any()
    private val _state = MutableStateFlow(VoiceSessionState())
    val state: StateFlow<VoiceSessionState> = _state.asStateFlow()

    private var observedTurnCount = 0
    private var expectedTurn = Int.MAX_VALUE
    private var sawBusy = false
    private var generationStarted = false
    private var replyComplete = false
    private var speechGeneration = 0
    private var pumpActive = false

    init {
        scope.launch {
            stt.events.collect { event ->
                when (event) {
                    is SttEvent.Partial -> onHeard(event.text, isFinal = false)
                    is SttEvent.Final -> onHeard(event.text, isFinal = true)
                    is SttEvent.Failure -> commands.send(VoiceCommand.Session(VoiceEvent.Failure(event.message, event.recoverable)))
                }
            }
        }
        scope.launch {
            stt.levels.collect { level ->
                if (_state.value.phase == VoicePhase.LISTENING) {
                    _state.value = _state.value.copy(level = level.coerceIn(0f, 1f))
                }
            }
        }
        scope.launch {
            vad.speechStarted.collect {
                val snapshot = _state.value
                if (BargeInPolicy.shouldInterrupt(
                        phase = snapshot.phase,
                        muted = snapshot.muted,
                        heard = null,
                        energy = 1f,
                        recentlySpoken = snapshot.recentlySpoken
                    )
                ) {
                    commands.send(VoiceCommand.Session(VoiceEvent.BargeIn))
                }
            }
        }
        scope.launch {
            vad.level.collect { level ->
                if (_state.value.phase == VoicePhase.SPEAKING || _state.value.phase == VoicePhase.THINKING) {
                    _state.value = _state.value.copy(level = level.coerceIn(0f, 1f))
                }
            }
        }
        scope.launch {
            audioFocus.focusLost.collect {
                commands.send(VoiceCommand.Session(VoiceEvent.AudioFocusLost))
            }
        }
        scope.launch {
            headset.usingHeadset.collect { connected ->
                commands.send(VoiceCommand.Session(VoiceEvent.HeadsetChanged(connected)))
            }
        }
        scope.launch {
            for (command in commands) {
                when (command) {
                    is VoiceCommand.Session -> apply(command.event)
                    is VoiceCommand.Snapshot -> handleSnapshot(command)
                }
            }
        }
    }

    fun start() = send(VoiceEvent.Start)

    fun mute() = send(VoiceEvent.Mute)

    fun unmute() = send(VoiceEvent.Unmute)

    fun interrupt() = send(VoiceEvent.Interrupt)

    fun end() = send(VoiceEvent.End)

    fun resume() = send(VoiceEvent.Resume)

    fun rejectSubmission(message: String) {
        commands.trySend(VoiceCommand.Session(VoiceEvent.SubmitRejected(message)))
    }

    fun onChatSnapshot(
        turnCount: Int,
        assistantText: String,
        busy: Boolean,
        awaitingContext: Boolean
    ) {
        observedTurnCount = turnCount
        commands.trySend(
            VoiceCommand.Snapshot(
                turnCount = turnCount,
                assistantText = assistantText,
                busy = busy,
                awaitingContext = awaitingContext
            )
        )
    }

    private fun send(event: VoiceEvent) {
        commands.trySend(VoiceCommand.Session(event))
    }

    private fun onHeard(text: String, isFinal: Boolean) {
        val snapshot = _state.value
        if (snapshot.phase == VoicePhase.SPEAKING || snapshot.phase == VoicePhase.THINKING) {
            if (BargeInPolicy.shouldInterrupt(
                    phase = snapshot.phase,
                    muted = snapshot.muted,
                    heard = text,
                    energy = 0f,
                    recentlySpoken = snapshot.recentlySpoken
                )
            ) {
                commands.trySend(VoiceCommand.Session(VoiceEvent.BargeIn))
            }
            return
        }
        val event = if (isFinal) {
            VoiceEvent.FinalUtterance(text)
        } else {
            VoiceEvent.PartialTranscript(text)
        }
        commands.trySend(VoiceCommand.Session(event))
    }

    private suspend fun handleSnapshot(event: VoiceCommand.Snapshot) {
        val phase = _state.value.phase
        if (phase != VoicePhase.THINKING && phase != VoicePhase.SPEAKING) return
        if (event.turnCount < expectedTurn) {
            if (event.busy || event.awaitingContext) sawBusy = true
            if (!event.busy && !event.awaitingContext && sawBusy) {
                apply(VoiceEvent.SubmitRejected("The message was not sent."))
            }
            return
        }
        if (event.busy || event.awaitingContext) {
            generationStarted = true
            val sentences = sentenceQueue.push(event.assistantText, flush = false)
            apply(
                VoiceEvent.ReplyProgress(
                    fullText = event.assistantText,
                    sentences = sentences,
                    replyComplete = false
                )
            )
            return
        }
        if (!generationStarted && event.assistantText.isBlank()) return
        generationStarted = true
        val sentences = sentenceQueue.push(event.assistantText, flush = true)
        replyComplete = true
        apply(
            VoiceEvent.ReplyProgress(
                fullText = event.assistantText,
                sentences = sentences,
                replyComplete = true
            )
        )
        if (sentences.isEmpty() && !pumpActive && _state.value.phase == VoicePhase.SPEAKING) {
            apply(VoiceEvent.PlaybackDrained)
        }
    }

    private suspend fun apply(event: VoiceEvent) {
        val transition = reducer.reduce(_state.value, event)
        _state.value = transition.state
        transition.effects.forEach { execute(it) }
    }

    private suspend fun execute(effect: VoiceEffect) {
        when (effect) {
            VoiceEffect.StartListening -> {
                val snapshot = _state.value
                if (!snapshot.muted && snapshot.phase == VoicePhase.LISTENING) {
                    stt.start()
                }
            }
            VoiceEffect.StopListening -> stt.stop()
            VoiceEffect.StartVad -> {
                val snapshot = _state.value
                if (!snapshot.muted &&
                    (snapshot.phase == VoicePhase.THINKING || snapshot.phase == VoicePhase.SPEAKING)
                ) {
                    vad.start()
                }
            }
            VoiceEffect.StopVad -> vad.stop()
            is VoiceEffect.Speak -> enqueueSpeech(effect.text)
            VoiceEffect.StopSpeaking -> cancelSpeech()
            is VoiceEffect.SubmitTurn -> {
                expectedTurn = observedTurnCount + 1
                sawBusy = false
                generationStarted = false
                replyComplete = false
                sentenceQueue.reset()
                cancelSpeech()
                if (!submitTurn(effect.text)) {
                    commands.trySend(VoiceCommand.Session(VoiceEvent.SubmitRejected("Wait for the current reply to finish.")))
                }
            }
            VoiceEffect.CancelReply -> cancelTurn()
            VoiceEffect.RequestAudioFocus -> audioFocus.requestPlayback()
            VoiceEffect.AbandonAudioFocus -> audioFocus.abandon()
            VoiceEffect.EngageHeadset -> headset.engage()
            VoiceEffect.RestoreAudioRoute -> headset.restore()
        }
    }

    private fun enqueueSpeech(text: String) {
        val generation = speechGeneration
        synchronized(speechLock) { pendingSpeech.addLast(text) }
        if (pumpActive) return
        pumpActive = true
        scope.launch {
            try {
                drainSpeech(generation)
            } finally {
                pumpActive = false
                val more = synchronized(speechLock) { pendingSpeech.isNotEmpty() }
                if (more && speechGeneration == generation) {
                    val next = synchronized(speechLock) { pendingSpeech.remove() }
                    enqueueSpeech(next)
                } else if (
                    speechGeneration == generation &&
                    replyComplete &&
                    _state.value.phase == VoicePhase.SPEAKING
                ) {
                    commands.trySend(VoiceCommand.Session(VoiceEvent.PlaybackDrained))
                }
            }
        }
    }

    private suspend fun drainSpeech(generation: Int) {
        while (speechGeneration == generation) {
            val next = synchronized(speechLock) { pendingSpeech.pollFirst() } ?: return
            tts.speak(UUID.randomUUID().toString(), next)
        }
    }

    private suspend fun cancelSpeech() {
        speechGeneration += 1
        synchronized(speechLock) { pendingSpeech.clear() }
        replyComplete = false
        tts.stop()
    }

    private sealed interface VoiceCommand {
        data class Session(val event: VoiceEvent) : VoiceCommand
        data class Snapshot(
            val turnCount: Int,
            val assistantText: String,
            val busy: Boolean,
            val awaitingContext: Boolean
        ) : VoiceCommand
    }
}
