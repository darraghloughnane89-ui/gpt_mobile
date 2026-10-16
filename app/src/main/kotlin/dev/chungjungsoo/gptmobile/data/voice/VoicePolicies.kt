package dev.chungjungsoo.gptmobile.data.voice

import dev.chungjungsoo.gptmobile.data.model.ClientType
import kotlin.math.sqrt

internal const val MAX_SPOKEN_CHUNK = 240

fun canAcceptVoiceUtterance(isBusy: Boolean, text: String): Boolean = !isBusy && text.isNotBlank()

object EchoGuard {
    fun isEcho(heard: String, recentlySpoken: String): Boolean {
        val heardTokens = tokens(heard)
        val spokenTokens = tokens(recentlySpoken)
        if (heardTokens.isEmpty() || spokenTokens.isEmpty()) return false
        val heardNorm = heardTokens.joinToString(" ")
        val spokenNorm = spokenTokens.joinToString(" ")
        if (spokenNorm.contains(heardNorm)) return true
        if (heardTokens.size < 3) return false
        val spokenSet = spokenTokens.toSet()
        val overlap = heardTokens.count { it in spokenSet }
        return overlap.toFloat() / heardTokens.size >= 0.72f
    }

    private fun tokens(value: String): List<String> = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
}

object BargeInPolicy {
    const val ENERGY_THRESHOLD = 0.045f

    fun shouldInterrupt(
        phase: VoicePhase,
        muted: Boolean,
        heard: String?,
        energy: Float,
        recentlySpoken: String
    ): Boolean {
        if (muted) return false
        if (phase != VoicePhase.SPEAKING && phase != VoicePhase.THINKING) return false
        val transcript = heard?.trim().orEmpty()
        if (transcript.isNotEmpty()) {
            return !EchoGuard.isEcho(transcript, recentlySpoken)
        }
        return energy >= ENERGY_THRESHOLD
    }
}

enum class SpeechGateSignal {
    NONE,
    SPEECH_STARTED,
    SPEECH_ENDED
}

class SpeechEnergyGate(
    private val threshold: Float = BargeInPolicy.ENERGY_THRESHOLD,
    private val speechWindows: Int = 3,
    private val silenceWindows: Int = 8
) {
    private var above = 0
    private var below = 0
    private var active = false

    fun reset() {
        above = 0
        below = 0
        active = false
    }

    fun onLevel(normalized: Float): SpeechGateSignal {
        if (normalized >= threshold) {
            above += 1
            below = 0
            if (!active && above >= speechWindows) {
                active = true
                return SpeechGateSignal.SPEECH_STARTED
            }
        } else {
            below += 1
            above = 0
            if (active && below >= silenceWindows) {
                active = false
                return SpeechGateSignal.SPEECH_ENDED
            }
        }
        return SpeechGateSignal.NONE
    }
}

internal fun normalizeRecognizerRms(rmsdB: Float): Float {
    val clamped = rmsdB.coerceIn(-2f, 10f)
    return (clamped + 2f) / 12f
}

internal fun pcmRms(buffer: ShortArray, count: Int): Float {
    if (count <= 0) return 0f
    var sum = 0.0
    for (index in 0 until count) {
        val sample = buffer[index].toDouble()
        sum += sample * sample
    }
    return (sqrt(sum / count) / 32768.0).toFloat()
}

internal object CommunicationDeviceType {
    const val WIRED_HEADSET = 3
    const val WIRED_HEADPHONES = 4
    const val BLUETOOTH_SCO = 7
    const val BLUETOOTH_A2DP = 8
    const val USB_HEADSET = 22
    const val BLE_HEADSET = 26
    const val BLE_SPEAKER = 27
}

internal fun preferredCommunicationDevice(types: List<Int>): Int? {
    val priority = listOf(
        CommunicationDeviceType.BLUETOOTH_SCO,
        CommunicationDeviceType.BLE_HEADSET,
        CommunicationDeviceType.BLE_SPEAKER,
        CommunicationDeviceType.BLUETOOTH_A2DP,
        CommunicationDeviceType.WIRED_HEADSET,
        CommunicationDeviceType.WIRED_HEADPHONES,
        CommunicationDeviceType.USB_HEADSET
    )
    return priority.firstOrNull { it in types }
}

data class OpenAiSpeechCandidate(
    val enabled: Boolean,
    val apiUrl: String,
    val token: String?,
    val clientType: ClientType
)

internal fun selectOpenAiSpeechPlatform(platforms: List<OpenAiSpeechCandidate>): OpenAiSpeechCandidate? {
    val withKey = platforms.filter { !it.token.isNullOrBlank() }
    return withKey.firstOrNull { it.enabled && it.apiUrl.contains("api.openai.com", ignoreCase = true) }
        ?: withKey.firstOrNull {
            it.clientType == ClientType.OPENAI && it.apiUrl.contains("openai.com", ignoreCase = true)
        }
}

internal fun openAiAudioUrl(baseUrl: String, path: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    return trimmed + "/" + path.trimStart('/')
}

internal fun openAiSpeechRequestJson(
    text: String,
    model: String = "tts-1",
    voice: String = "alloy"
): String {
    val payload = kotlinx.serialization.json.buildJsonObject {
        put("model", kotlinx.serialization.json.JsonPrimitive(model))
        put("voice", kotlinx.serialization.json.JsonPrimitive(voice))
        put("input", kotlinx.serialization.json.JsonPrimitive(text))
    }
    return payload.toString()
}

internal fun pcm16ToWav(pcm: ByteArray, sampleRate: Int, channels: Int = 1): ByteArray {
    val bitsPerSample = 16
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    val header = ByteArray(44)
    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    writeInt(header, 4, 36 + pcm.size)
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    writeInt(header, 16, 16)
    writeShort(header, 20, 1)
    writeShort(header, 22, channels)
    writeInt(header, 24, sampleRate)
    writeInt(header, 28, byteRate)
    writeShort(header, 32, blockAlign)
    writeShort(header, 34, bitsPerSample)
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    writeInt(header, 40, pcm.size)
    return header + pcm
}

private fun writeInt(target: ByteArray, offset: Int, value: Int) {
    target[offset] = (value and 0xff).toByte()
    target[offset + 1] = (value shr 8 and 0xff).toByte()
    target[offset + 2] = (value shr 16 and 0xff).toByte()
    target[offset + 3] = (value shr 24 and 0xff).toByte()
}

private fun writeShort(target: ByteArray, offset: Int, value: Int) {
    target[offset] = (value and 0xff).toByte()
    target[offset + 1] = (value shr 8 and 0xff).toByte()
}

internal fun bestTranscript(candidates: List<String>?): String = candidates
    ?.firstOrNull { it.isNotBlank() }
    .orEmpty()

object VoiceLaunch {
    const val EXTRA_OPEN_VOICE = "dev.chungjungsoo.gptmobile.OPEN_VOICE"
    const val ACTION_ASSIST = "android.intent.action.ASSIST"
    const val ACTION_VOICE_COMMAND = "android.intent.action.VOICE_COMMAND"

    fun isVoiceEntry(action: String?, openVoiceExtra: Boolean): Boolean {
        if (openVoiceExtra) return true
        return action == ACTION_ASSIST || action == ACTION_VOICE_COMMAND
    }
}
