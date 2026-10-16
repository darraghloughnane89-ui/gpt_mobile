package dev.chungjungsoo.gptmobile.data.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Hands-free route and speech router that later voice work can adopt.
 *
 * `voice_session` is the destination named by the hands-free session work.
 * [VoiceSpeechRouterHost] is the recognizer and speaker that work should inject:
 * [VoiceRecognizer.start] while listening, [VoiceSpeaker.speak] per reply sentence,
 * and [VoiceSpeaker.stop] for barge-in and the stop control.
 * Neither the session reducer nor a cloud speech router is on main, so this
 * module keeps those names and method shapes and ships an on-device fallback.
 */
object VoiceSessionAdoption {
    const val ROUTE = "voice_session"

    fun destination(chatRoomId: Int, enabledPlatforms: String): String =
        "$ROUTE?chatRoomId=$chatRoomId&enabled=$enabledPlatforms"
}

sealed interface VoiceTranscript {
    data class Partial(val text: String) : VoiceTranscript
    data class Final(val text: String) : VoiceTranscript
    data class Failure(val message: String, val recoverable: Boolean) : VoiceTranscript
}

interface VoiceRecognizer {
    val events: Flow<VoiceTranscript>
    val levels: Flow<Float>
        get() = emptyFlow()

    suspend fun start()

    suspend fun stop()
}

interface VoiceSpeaker {
    suspend fun speak(utteranceId: String, text: String)

    /** Stops playback immediately. Barge-in and the stop control both call this. */
    suspend fun stop()
}

interface VoiceSpeechRouterHost {
    val recognizer: VoiceRecognizer
    val speaker: VoiceSpeaker

    suspend fun refresh()
}

fun canAcceptVoiceUtterance(isBusy: Boolean, text: String): Boolean = !isBusy && text.isNotBlank()
