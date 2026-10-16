package dev.chungjungsoo.gptmobile.presentation.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.voice.AudioFocusGateway
import dev.chungjungsoo.gptmobile.data.voice.HeadsetRouter
import dev.chungjungsoo.gptmobile.data.voice.SpeechToText
import dev.chungjungsoo.gptmobile.data.voice.TextToSpeechEngine
import dev.chungjungsoo.gptmobile.data.voice.VoiceActivityDetector
import dev.chungjungsoo.gptmobile.data.voice.VoiceConversation
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@HiltViewModel
class VoiceSessionViewModel @Inject constructor(
    speechToText: SpeechToText,
    textToSpeech: TextToSpeechEngine,
    voiceActivityDetector: VoiceActivityDetector,
    audioFocusGateway: AudioFocusGateway,
    headsetRouter: HeadsetRouter
) : ViewModel() {
    private val submitted = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val submittedUtterances = submitted.asSharedFlow()
    private val cancelled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val replyCancellations = cancelled.asSharedFlow()

    private val conversation = VoiceConversation(
        stt = speechToText,
        tts = textToSpeech,
        vad = voiceActivityDetector,
        audioFocus = audioFocusGateway,
        headset = headsetRouter,
        scope = viewModelScope,
        submitTurn = { text -> submitted.tryEmit(text) },
        cancelTurn = { cancelled.tryEmit(Unit) }
    )

    val state = conversation.state

    fun start() = conversation.start()

    fun mute() = conversation.mute()

    fun unmute() = conversation.unmute()

    fun interrupt() = conversation.interrupt()

    fun end() = conversation.end()

    fun resume() = conversation.resume()

    fun onChatSnapshot(
        turnCount: Int,
        assistantText: String,
        busy: Boolean,
        awaitingContext: Boolean
    ) = conversation.onChatSnapshot(turnCount, assistantText, busy, awaitingContext)

    fun onUtteranceRejected() = conversation.rejectSubmission("Wait for the current reply to finish.")
}
