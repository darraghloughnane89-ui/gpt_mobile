package dev.chungjungsoo.gptmobile.data.voice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

sealed interface SttEvent {
    data class Partial(val text: String) : SttEvent
    data class Final(val text: String) : SttEvent
    data class Failure(val message: String, val recoverable: Boolean) : SttEvent
}

interface SpeechToText {
    val events: Flow<SttEvent>
    val levels: Flow<Float>
        get() = emptyFlow()

    suspend fun start()

    suspend fun stop()
}

interface TextToSpeechEngine {
    suspend fun speak(utteranceId: String, text: String)

    suspend fun stop()
}

interface VoiceActivityDetector {
    val speechStarted: Flow<Unit>
    val level: Flow<Float>

    suspend fun start()

    suspend fun stop()
}

interface AudioFocusGateway {
    val focusLost: Flow<Unit>

    fun requestPlayback(): Boolean

    fun abandon()
}

interface HeadsetRouter {
    val usingHeadset: StateFlow<Boolean>

    fun engage()

    fun restore()
}

enum class VoiceEngine {
    DEVICE,
    OPENAI
}

data class VoiceSettings(
    val speechRecognition: VoiceEngine = VoiceEngine.DEVICE,
    val speechSynthesis: VoiceEngine = VoiceEngine.DEVICE
)

interface VoicePreferences {
    val settings: Flow<VoiceSettings>

    suspend fun setSpeechRecognition(engine: VoiceEngine)

    suspend fun setSpeechSynthesis(engine: VoiceEngine)
}

data class VoiceProviderCredential(
    val apiKey: String,
    val baseUrl: String
) {
    override fun toString(): String = "VoiceProviderCredential(baseUrl=$baseUrl)"
}

interface VoiceCredentialLookup {
    suspend fun openAiCredential(): VoiceProviderCredential?
}

interface OpenAiAudioClient {
    suspend fun transcribe(credential: VoiceProviderCredential, wav: ByteArray): String

    suspend fun synthesize(credential: VoiceProviderCredential, text: String): ByteArray
}
