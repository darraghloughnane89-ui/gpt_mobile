package dev.chungjungsoo.gptmobile.data.voice

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Xiaomi MiMo speech ids, checked 2026-10-16.
 *
 * Model ids: https://mimo.mi.com/docs/en-US/quick-start/summary/model
 * ASR: https://mimo.mi.com/docs/en-US/api/audio/Speech-Recognition
 * TTS: https://mimo.mi.com/docs/en-US/api/audio/tts
 * Streaming PCM: https://mimo.mi.com/docs/en-US/quick-start/usage-guide/audio/speech-synthesis-v2.5
 * Europe token plan: https://mimo.mi.com/docs/en-US/tokenplan/Token%20Plan/quick-access
 */
object MiMoSpeechModels {
    const val BASE_URL = "https://token-plan-ams.xiaomimimo.com/v1"
    const val COMPLETIONS_PATH = "chat/completions"
    const val ASR = "mimo-v2.5-asr"
    const val TTS = "mimo-v2.5-tts"
    const val TTS_VOICE_CLONE = "mimo-v2.5-tts-voiceclone"
    const val TTS_VOICE_DESIGN = "mimo-v2.5-tts-voicedesign"
    const val DEFAULT_VOICE = "mimo_default"
    const val TTS_SAMPLE_RATE_HZ = 24_000
    const val ASR_SAMPLE_RATE_HZ = 16_000
    const val SECRET_REF = "voice-mimo-api-key"

    val builtinVoices = listOf(
        MiMoVoice("mimo_default", "MiMo default", "Cluster default"),
        MiMoVoice("冰糖", "冰糖", "Chinese"),
        MiMoVoice("茉莉", "茉莉", "Chinese"),
        MiMoVoice("苏打", "苏打", "Chinese"),
        MiMoVoice("白桦", "白桦", "Chinese"),
        MiMoVoice("Mia", "Mia", "English"),
        MiMoVoice("Chloe", "Chloe", "English"),
        MiMoVoice("Milo", "Milo", "English"),
        MiMoVoice("Dean", "Dean", "English")
    )

    fun knownVoice(voiceId: String?): String = builtinVoices.firstOrNull { it.id == voiceId }?.id ?: DEFAULT_VOICE
}

data class MiMoVoice(
    val id: String,
    val label: String,
    val language: String
)

class MiMoKeyCandidate(
    val enabled: Boolean,
    val apiUrl: String,
    val token: String?
) {
    override fun toString(): String = "MiMoKeyCandidate(enabled=$enabled, apiUrl=$apiUrl, hasToken=${!token.isNullOrBlank()})"
}

object MiMoSpeechProtocol {
    private val json = Json { ignoreUnknownKeys = true }

    fun completionsUrl(baseUrl: String = MiMoSpeechModels.BASE_URL): String {
        val root = baseUrl.trim().trimEnd('/')
        return "$root/${MiMoSpeechModels.COMPLETIONS_PATH}"
    }

    fun isEuropeTokenPlan(apiUrl: String): Boolean = apiUrl.trim().trimEnd('/').equals(MiMoSpeechModels.BASE_URL, ignoreCase = true)

    fun transcriptionBody(mimeType: String, audioBase64: String, language: String = "auto"): String = buildJsonObject {
        put("model", MiMoSpeechModels.ASR)
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(
                            buildJsonObject {
                                put("type", "input_audio")
                                putJsonObject("input_audio") {
                                    put("data", "data:$mimeType;base64,$audioBase64")
                                }
                            }
                        )
                    }
                }
            )
        }
        putJsonObject("asr_options") {
            put("language", language.ifBlank { "auto" })
        }
    }.toString()

    fun synthesisBody(text: String, voice: String, stream: Boolean): String = buildJsonObject {
        put("model", MiMoSpeechModels.TTS)
        put("stream", stream)
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "assistant")
                    put("content", text)
                }
            )
        }
        putJsonObject("audio") {
            put("format", if (stream) "pcm16" else "wav")
            put("voice", MiMoSpeechModels.knownVoice(voice))
        }
    }.toString()

    fun transcript(body: String): String {
        val message = messageObject(body) ?: return ""
        return message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    fun audioChunks(line: String): ByteArray? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val payload = if (trimmed.startsWith("data:")) {
            trimmed.removePrefix("data:").trim()
        } else if (trimmed.startsWith("{")) {
            trimmed
        } else {
            return null
        }
        if (payload.isEmpty() || payload == "[DONE]") return null
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
        speechFailure(root)?.let { message -> throw MiMoSpeechException(message) }
        val choice = root["choices"]?.jsonArray?.firstOrNull() as? JsonObject ?: return null
        val audio = choice["delta"]?.let { audioElement(it) } ?: choice["message"]?.let { audioElement(it) }
        return decodeAudio(audio)
    }

    fun redact(status: Int, body: String, apiKey: String): String {
        val cleaned = if (apiKey.isBlank()) body else body.replace(apiKey, "***")
        return "MiMo speech failed ($status): ${cleaned.take(180)}"
    }

    fun keyPreview(key: String): String {
        val trimmed = key.trim()
        if (trimmed.length < 8) return ""
        return trimmed.takeLast(4)
    }

    fun selectKey(dedicated: String?, platforms: List<MiMoKeyCandidate>): String? {
        dedicated?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val matches = platforms.filter { candidate ->
            !candidate.token.isNullOrBlank() && isEuropeTokenPlan(candidate.apiUrl)
        }
        val chosen = matches.firstOrNull { it.enabled } ?: matches.firstOrNull()
        return chosen?.token?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun messageObject(body: String): JsonObject? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val choice = root["choices"]?.jsonArray?.firstOrNull() as? JsonObject ?: return null
        return choice["message"] as? JsonObject
    }

    private fun audioElement(holder: JsonElement): String? {
        val obj = holder as? JsonObject ?: return null
        val audio = obj["audio"] ?: return null
        return when (audio) {
            is JsonObject -> audio["data"]?.let { it as? JsonPrimitive }?.contentOrNull
            is JsonPrimitive -> audio.contentOrNull
            else -> null
        }
    }

    private fun decodeAudio(encoded: String?): ByteArray? {
        if (encoded.isNullOrBlank()) return null
        val payload = encoded.substringAfter("base64,", encoded).trim()
        if (payload.isEmpty()) return null
        return runCatching { Base64.getDecoder().decode(payload) }.getOrNull()
    }

    private fun speechFailure(root: JsonObject): String? {
        val error = root["error"] as? JsonObject ?: return null
        return error["message"]?.let { it as? JsonPrimitive }?.contentOrNull ?: "MiMo speech failed"
    }
}

class MiMoSpeechException(message: String) : Exception(message)

internal class Pcm16Framer {
    private var pending: Byte? = null

    fun frame(chunk: ByteArray): ByteArray {
        if (chunk.isEmpty() && pending == null) return ByteArray(0)
        val extra = if (pending != null) 1 else 0
        val combined = ByteArray(extra + chunk.size)
        var index = 0
        pending?.let { byte ->
            combined[index] = byte
            index += 1
        }
        chunk.copyInto(combined, index)
        pending = null
        if (combined.size % 2 == 1) {
            pending = combined.last()
        }
        return combined.copyOf(combined.size - combined.size % 2)
    }
}
