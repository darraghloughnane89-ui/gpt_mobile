package dev.chungjungsoo.gptmobile.presentation.ui.voice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.voice.HandsFreeEffect
import dev.chungjungsoo.gptmobile.data.voice.HandsFreeEvent
import dev.chungjungsoo.gptmobile.data.voice.HandsFreePhase
import dev.chungjungsoo.gptmobile.data.voice.HandsFreePreferences
import dev.chungjungsoo.gptmobile.data.voice.HandsFreeReducer
import dev.chungjungsoo.gptmobile.data.voice.HandsFreeState
import dev.chungjungsoo.gptmobile.data.voice.NotificationCaptureGate
import dev.chungjungsoo.gptmobile.data.voice.ReplySentenceSplitter
import dev.chungjungsoo.gptmobile.data.voice.VoiceSpeechRouterHost
import dev.chungjungsoo.gptmobile.data.voice.VoiceTranscript
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HandsFreeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val router: VoiceSpeechRouterHost,
    private val preferences: HandsFreePreferences,
    gate: NotificationCaptureGate
) : ViewModel() {
    val captureAllowed: StateFlow<Boolean> = gate.notificationVisible
    val chatRoomId: Int = savedStateHandle.get<Int>("chatRoomId") ?: 0
    val enabledPlatforms: String = savedStateHandle.get<String>("enabledPlatforms").orEmpty()

    private val reducer = HandsFreeReducer()
    private val splitter = ReplySentenceSplitter()
    private val _state = MutableStateFlow(HandsFreeState())
    val state: StateFlow<HandsFreeState> = _state.asStateFlow()
    private val _pendingUtterance = MutableStateFlow<String?>(null)
    val pendingUtterance: StateFlow<String?> = _pendingUtterance.asStateFlow()
    private val leaveActions = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val leaveToWake: SharedFlow<Boolean> = leaveActions.asSharedFlow()

    private var listenJob: Job? = null
    private var observedTurns = 0
    private var replyBaseline = -1
    private var replyFinished = false
    private var speakJobs = 0

    init {
        viewModelScope.launch {
            preferences.rememberSession(chatRoomId, enabledPlatforms)
        }
    }

    fun startSession() {
        dispatch(HandsFreeEvent.Start)
    }

    fun endSession() {
        dispatch(HandsFreeEvent.End)
    }

    fun leave() {
        dispatch(HandsFreeEvent.End)
        viewModelScope.launch {
            leaveActions.emit(preferences.current().wakeEnabled)
        }
    }

    fun interrupt() {
        dispatch(HandsFreeEvent.Interrupt)
    }

    fun toggleMute() {
        dispatch(if (_state.value.muted) HandsFreeEvent.Unmute else HandsFreeEvent.Mute)
    }

    fun onCaptureBlocked() {
        dispatch(HandsFreeEvent.CaptureBlocked)
    }

    fun onBatteryStop() {
        dispatch(HandsFreeEvent.BatteryStop)
    }

    fun onChatSnapshot(turnCount: Int, replyText: String, busy: Boolean) {
        observedTurns = turnCount
        val phase = _state.value.phase
        if (phase != HandsFreePhase.THINKING && phase != HandsFreePhase.SPEAKING) return
        if (replyBaseline < 0 || turnCount <= replyBaseline) return
        val pieces = splitter.consume(replyText, complete = !busy)
        replyFinished = !busy
        when {
            pieces.isNotEmpty() -> dispatch(
                HandsFreeEvent.ReplyProgress(fullText = replyText, sentences = pieces, replyComplete = false)
            )
            !busy && phase == HandsFreePhase.THINKING -> dispatch(
                HandsFreeEvent.ReplyProgress(fullText = replyText, sentences = emptyList(), replyComplete = true)
            )
            !busy && speakJobs == 0 && phase == HandsFreePhase.SPEAKING -> dispatch(HandsFreeEvent.PlaybackDrained)
        }
    }

    fun consumeUtterance(accepted: Boolean) {
        _pendingUtterance.value = null
        if (!accepted) {
            dispatch(HandsFreeEvent.SubmitRejected("Still working on the last reply."))
        }
    }

    override fun onCleared() {
        listenJob?.cancel()
        viewModelScope.launch { router.speaker.stop() }
        super.onCleared()
    }

    private fun dispatch(event: HandsFreeEvent) {
        val transition = reducer.reduce(_state.value, event)
        _state.value = transition.state
        transition.effects.forEach(::apply)
    }

    private fun apply(effect: HandsFreeEffect) {
        when (effect) {
            HandsFreeEffect.StartListening -> startListening()
            HandsFreeEffect.StopListening -> stopListening()
            is HandsFreeEffect.Speak -> speak(effect.text)
            HandsFreeEffect.StopSpeaking -> stopSpeaking()
            is HandsFreeEffect.SubmitTurn -> {
                replyBaseline = observedTurns
                replyFinished = false
                splitter.reset()
                _pendingUtterance.value = effect.text
            }
            HandsFreeEffect.CancelReply -> {
                replyFinished = true
                stopSpeaking()
            }
            HandsFreeEffect.ReleaseCapture -> stopListening()
        }
    }

    private fun startListening() {
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            val events = launch {
                router.recognizer.events.collect { transcript ->
                    when (transcript) {
                        is VoiceTranscript.Partial -> dispatch(HandsFreeEvent.PartialTranscript(transcript.text))
                        is VoiceTranscript.Final -> dispatch(HandsFreeEvent.FinalUtterance(transcript.text))
                        is VoiceTranscript.Failure -> {
                            if (transcript.recoverable && _state.value.phase == HandsFreePhase.LISTENING) {
                                viewModelScope.launch {
                                    delay(300)
                                    if (_state.value.phase == HandsFreePhase.LISTENING && !_state.value.muted) {
                                        startListening()
                                    }
                                }
                            } else {
                                dispatch(HandsFreeEvent.Failure(transcript.message, transcript.recoverable))
                            }
                        }
                    }
                }
            }
            try {
                router.refresh()
                router.recognizer.start()
                awaitCancellation()
            } finally {
                events.cancel()
                router.recognizer.stop()
            }
        }
    }

    private fun stopListening() {
        listenJob?.cancel()
        listenJob = null
    }

    private fun speak(text: String) {
        val utteranceId = UUID.randomUUID().toString()
        speakJobs += 1
        viewModelScope.launch {
            try {
                router.speaker.speak(utteranceId, text)
            } finally {
                speakJobs = (speakJobs - 1).coerceAtLeast(0)
                if (speakJobs == 0 && replyFinished && _state.value.phase == HandsFreePhase.SPEAKING) {
                    dispatch(HandsFreeEvent.PlaybackDrained)
                }
            }
        }
    }

    private fun stopSpeaking() {
        speakJobs = 0
        viewModelScope.launch { router.speaker.stop() }
    }
}
