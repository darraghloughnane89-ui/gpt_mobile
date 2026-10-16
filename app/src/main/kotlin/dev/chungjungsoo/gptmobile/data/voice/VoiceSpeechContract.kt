package dev.chungjungsoo.gptmobile.data.voice

import kotlin.math.sqrt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Hands-free voice route from the voice-session work. That destination is not
 * registered on main. [VoiceSpeechRouter] is the recognizer and speaker that
 * route should inject.
 */
object VoiceSessionAdoption {
    const val ROUTE = "voice_session"
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

enum class VoiceSpeechBackend {
    DEVICE,
    MIMO
}

data class VoiceSpeechSettings(
    val useMiMo: Boolean = false,
    val voiceId: String = MiMoSpeechModels.DEFAULT_VOICE
)

interface VoiceSpeechPreferences {
    val settings: Flow<VoiceSpeechSettings>

    suspend fun setUseMiMo(enabled: Boolean)

    suspend fun setVoiceId(voiceId: String)
}

interface VoiceSpeechKeyStore {
    suspend fun hasKey(): Boolean

    suspend fun readKey(): String?

    suspend fun saveKey(key: String)

    suspend fun clearKey()

    suspend fun preview(): String
}

enum class VoiceBackendStatus {
    MIMO,
    DEVICE_BY_CHOICE,
    DEVICE_MISSING_KEY
}

fun selectVoiceBackend(useMiMo: Boolean, hasKey: Boolean): VoiceSpeechBackend = if (useMiMo && hasKey) {
    VoiceSpeechBackend.MIMO
} else {
    VoiceSpeechBackend.DEVICE
}

fun voiceBackendStatus(useMiMo: Boolean, hasKey: Boolean): VoiceBackendStatus = when {
    useMiMo && hasKey -> VoiceBackendStatus.MIMO
    useMiMo -> VoiceBackendStatus.DEVICE_MISSING_KEY
    else -> VoiceBackendStatus.DEVICE_BY_CHOICE
}

enum class SpeechGateSignal {
    NONE,
    SPEECH_STARTED,
    SPEECH_ENDED
}

class SpeechEnergyGate(
    private val threshold: Float = BARGE_IN_ENERGY,
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

    companion object {
        const val BARGE_IN_ENERGY = 0.045f
    }
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

internal fun shortsToLittleEndian(samples: ShortArray, count: Int): ByteArray {
    val bytes = ByteArray(count * 2)
    for (index in 0 until count) {
        val value = samples[index].toInt()
        bytes[index * 2] = (value and 0xff).toByte()
        bytes[index * 2 + 1] = (value shr 8 and 0xff).toByte()
    }
    return bytes
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

interface VoiceSpeechRouterHost {
    val recognizer: VoiceRecognizer
    val speaker: VoiceSpeaker
    val activeBackend: StateFlow<VoiceSpeechBackend>

    suspend fun refresh()
}
