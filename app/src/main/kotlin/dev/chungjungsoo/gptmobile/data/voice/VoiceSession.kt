package dev.chungjungsoo.gptmobile.data.voice

enum class VoicePhase {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    PAUSED,
    ENDED
}

data class VoiceSessionState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val muted: Boolean = false,
    val partialTranscript: String = "",
    val lastUserUtterance: String = "",
    val replyText: String = "",
    val recentlySpoken: String = "",
    val level: Float = 0f,
    val usingHeadset: Boolean = false,
    val bargeInCount: Int = 0,
    val statusMessage: String? = null
)

sealed interface VoiceEvent {
    data object Start : VoiceEvent
    data class PartialTranscript(val text: String) : VoiceEvent
    data class FinalUtterance(val text: String) : VoiceEvent
    data class ReplyProgress(
        val fullText: String,
        val sentences: List<String>,
        val replyComplete: Boolean
    ) : VoiceEvent
    data object PlaybackDrained : VoiceEvent
    data object BargeIn : VoiceEvent
    data object Interrupt : VoiceEvent
    data object Mute : VoiceEvent
    data object Unmute : VoiceEvent
    data object End : VoiceEvent
    data object AudioFocusLost : VoiceEvent
    data object Resume : VoiceEvent
    data class SubmitRejected(val message: String) : VoiceEvent
    data class Failure(val message: String, val recoverable: Boolean) : VoiceEvent
    data class HeadsetChanged(val connected: Boolean) : VoiceEvent
    data class LevelChanged(val level: Float) : VoiceEvent
}

sealed interface VoiceEffect {
    data object StartListening : VoiceEffect
    data object StopListening : VoiceEffect
    data object StartVad : VoiceEffect
    data object StopVad : VoiceEffect
    data class Speak(val text: String) : VoiceEffect
    data object StopSpeaking : VoiceEffect
    data class SubmitTurn(val text: String) : VoiceEffect
    data object CancelReply : VoiceEffect
    data object RequestAudioFocus : VoiceEffect
    data object AbandonAudioFocus : VoiceEffect
    data object EngageHeadset : VoiceEffect
    data object RestoreAudioRoute : VoiceEffect
}

data class VoiceTransition(
    val state: VoiceSessionState,
    val effects: List<VoiceEffect> = emptyList()
)
