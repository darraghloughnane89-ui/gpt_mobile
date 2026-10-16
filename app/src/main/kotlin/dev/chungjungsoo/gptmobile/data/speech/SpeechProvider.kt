package dev.chungjungsoo.gptmobile.data.speech

/**
 * Speech synthesis and recognition for a provider that publishes those models.
 * Chat model lists stay separate. A voice feature can depend on this interface
 * without knowing Xiaomi's request shape.
 */
interface SpeechProvider {
    val id: String
    val asrModelId: String
    val ttsModelId: String

    suspend fun transcribe(request: SpeechTranscriptionRequest): SpeechTranscript

    suspend fun synthesize(request: SpeechSynthesisRequest): SpeechAudio
}

data class SpeechTranscriptionRequest(
    val audioBase64: String,
    val mimeType: String,
    val language: String = "auto"
)

data class SpeechSynthesisRequest(
    val text: String,
    val style: String? = null,
    val voice: String = MiMoSpeechModels.DEFAULT_VOICE,
    val format: String = "wav",
    val modelId: String = MiMoSpeechModels.TTS
)

data class SpeechTranscript(val text: String)

data class SpeechAudio(
    val bytes: ByteArray,
    val mimeType: String
)
