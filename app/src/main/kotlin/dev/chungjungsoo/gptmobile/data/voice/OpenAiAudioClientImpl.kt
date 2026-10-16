package dev.chungjungsoo.gptmobile.data.voice

import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

class OpenAiAudioClientImpl(
    private val networkClient: NetworkClient
) : OpenAiAudioClient {
    override suspend fun transcribe(credential: VoiceProviderCredential, wav: ByteArray): String {
        val response = networkClient().post(openAiAudioUrl(credential.baseUrl, "audio/transcriptions")) {
            bearerAuth(credential.apiKey)
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("model", TRANSCRIPTION_MODEL)
                        append(
                            "file",
                            wav,
                            Headers.build {
                                append(HttpHeaders.ContentType, "audio/wav")
                                append(HttpHeaders.ContentDisposition, "filename=\"speech.wav\"")
                            }
                        )
                    }
                )
            )
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error(safeSpeechError(response.status.value, body, credential.apiKey))
        }
        return NetworkClient.json.decodeFromString<OpenAiTranscript>(body).text
    }

    override suspend fun synthesize(credential: VoiceProviderCredential, text: String): ByteArray {
        val response = networkClient().post(openAiAudioUrl(credential.baseUrl, "audio/speech")) {
            bearerAuth(credential.apiKey)
            contentType(ContentType.Application.Json)
            setBody(openAiSpeechRequestJson(text))
        }
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            error(safeSpeechError(response.status.value, body, credential.apiKey))
        }
        return response.bodyAsBytes()
    }

    private companion object {
        const val TRANSCRIPTION_MODEL = "whisper-1"
    }
}

@Serializable
private data class OpenAiTranscript(val text: String = "")

internal fun safeSpeechError(status: Int, body: String, apiKey: String): String {
    val redacted = if (apiKey.isBlank()) body else body.replace(apiKey, "***")
    return "OpenAI speech failed ($status): ${redacted.take(180)}"
}
