package dev.chungjungsoo.gptmobile.data.speech

import dev.chungjungsoo.gptmobile.data.network.applyCompatibleCredentials
import io.ktor.client.HttpClient
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Model ids from Xiaomi's model summary and audio docs, checked 2026-10-15:
 * https://mimo.mi.com/docs/en-US/quick-start/summary/model
 * https://mimo.mi.com/docs/en-US/api/audio/Speech-Recognition
 * https://mimo.mi.com/docs/en-US/api/audio/tts
 */
object MiMoSpeechModels {
    const val ASR = "mimo-v2.5-asr"
    const val TTS = "mimo-v2.5-tts"
    const val TTS_VOICE_CLONE = "mimo-v2.5-tts-voiceclone"
    const val TTS_VOICE_DESIGN = "mimo-v2.5-tts-voicedesign"
    const val DEFAULT_VOICE = "mimo_default"
    val chatModelIds = setOf("mimo-v2.6-pro", "mimo-v2.6-flash", "mimo-v2.5-pro")
}

object MiMoSpeechRequests {
    private val json = Json { ignoreUnknownKeys = true }

    fun transcriptionBody(request: SpeechTranscriptionRequest): String = buildJsonObject {
        put("model", MiMoSpeechModels.ASR)
        putJsonArray("messages") {
            add(buildJsonObject {
                put("role", "user")
                putJsonArray("content") {
                    add(buildJsonObject {
                        put("type", "input_audio")
                        putJsonObject("input_audio") {
                            put("data", "data:${request.mimeType};base64,${request.audioBase64}")
                        }
                    })
                }
            })
        }
        putJsonObject("asr_options") {
            put("language", request.language.ifBlank { "auto" })
        }
    }.toString()

    fun synthesisBody(request: SpeechSynthesisRequest): String = buildJsonObject {
        put("model", request.modelId.ifBlank { MiMoSpeechModels.TTS })
        putJsonArray("messages") {
            request.style?.takeIf { it.isNotBlank() }?.let { style ->
                add(buildJsonObject {
                    put("role", "user")
                    put("content", style)
                })
            }
            add(buildJsonObject {
                put("role", "assistant")
                put("content", request.text)
            })
        }
        putJsonObject("audio") {
            put("format", request.format.ifBlank { "wav" })
            if (request.modelId != MiMoSpeechModels.TTS_VOICE_DESIGN && request.voice.isNotBlank()) {
                put("voice", request.voice)
            }
        }
    }.toString()

    fun parseTranscript(body: String): String {
        val message = messageObject(body) ?: return ""
        return message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    fun parseAudioBase64(body: String): String {
        val message = messageObject(body) ?: return ""
        val audio = message["audio"] as? JsonObject ?: return ""
        return audio["data"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun messageObject(body: String): JsonObject? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val choice = root["choices"]?.jsonArray?.firstOrNull() as? JsonObject ?: return null
        return choice["message"] as? JsonObject
    }
}

class MiMoSpeechProvider(
    private val httpClient: HttpClient,
    private val apiUrl: String,
    private val apiKey: String
) : SpeechProvider {
    override val id: String = "mimo"
    override val asrModelId: String = MiMoSpeechModels.ASR
    override val ttsModelId: String = MiMoSpeechModels.TTS

    override suspend fun transcribe(request: SpeechTranscriptionRequest): SpeechTranscript {
        val body = post(MiMoSpeechRequests.transcriptionBody(request))
        return SpeechTranscript(MiMoSpeechRequests.parseTranscript(body))
    }

    override suspend fun synthesize(request: SpeechSynthesisRequest): SpeechAudio {
        val body = post(MiMoSpeechRequests.synthesisBody(request))
        val encoded = MiMoSpeechRequests.parseAudioBase64(body)
        val bytes = runCatching { java.util.Base64.getDecoder().decode(encoded) }.getOrDefault(ByteArray(0))
        val mime = if (request.format.equals("wav", ignoreCase = true)) "audio/wav" else "application/octet-stream"
        return SpeechAudio(bytes, mime)
    }

    private suspend fun post(payload: String): String {
        val root = apiUrl.trim().trimEnd('/')
        val endpoint = "$root/chat/completions"
        return httpClient.preparePost(endpoint) {
            contentType(ContentType.Application.Json)
            applyCompatibleCredentials(apiUrl, apiKey)
            setBody(payload)
        }.execute { response ->
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                error("MiMo speech request failed (${response.status.value})")
            }
            text
        }
    }
}
