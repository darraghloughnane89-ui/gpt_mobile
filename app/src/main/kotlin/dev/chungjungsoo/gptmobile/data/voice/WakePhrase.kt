package dev.chungjungsoo.gptmobile.data.voice

object WakePhrase {
    const val SPOKEN = "hey robin"
    const val DISPLAY = "Hey Robin"

    fun matches(transcript: String): Boolean = normalize(transcript).let { norm ->
        norm == SPOKEN || norm.startsWith("$SPOKEN ")
    }

    fun normalize(transcript: String): String = transcript
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

data class WakeEngineLicence(
    val engine: String,
    val spdx: String,
    val url: String,
    val note: String
) {
    companion object {
        /**
         * openWakeWord is Apache-2.0. Vosk is Apache-2.0. ONNX Runtime is MIT.
         * Picovoice Porcupine is proprietary and needs a paid access key, so it is not used.
         */
        val OPEN_WAKE_WORD = WakeEngineLicence(
            engine = "openWakeWord-compatible",
            spdx = "Apache-2.0",
            url = "https://github.com/dscripka/openWakeWord",
            note = "On-device scorer. 16 kHz mono PCM and 80 ms frames follow the openWakeWord " +
                "contract. The hey-robin template lives in this app. Vosk " +
                "(https://github.com/alphacep/vosk-api, Apache-2.0) or an ONNX Runtime session " +
                "(https://github.com/microsoft/onnxruntime, MIT) can replace WakePhraseEngine. " +
                "No Picovoice key."
        )
    }
}

data class WakeScore(
    val similarity: Float,
    val detected: Boolean
)

interface WakePhraseEngine {
    val phrase: String
    val licence: WakeEngineLicence

    fun reset()

    fun accept(pcm16: ShortArray): WakeScore
}

/** Scores one feature vector. An ONNX session adopts the wake engine through this. */
fun interface OnnxWakeFeatureRunner {
    fun score(features: FloatArray): Float
}

class WakeDebouncer(private val windowMs: Long = 2_000L) {
    private var lastAcceptedMs = 0L
    private var hasAccepted = false

    fun tryAccept(nowMs: Long): Boolean {
        if (hasAccepted && nowMs - lastAcceptedMs < windowMs) return false
        lastAcceptedMs = nowMs
        hasAccepted = true
        return true
    }

    fun reset() {
        lastAcceptedMs = 0L
        hasAccepted = false
    }
}

enum class WakeFrameKind {
    BLOCKED,
    LISTENING,
    DETECTED
}

data class WakeFrameResult(
    val kind: WakeFrameKind,
    val similarity: Float = 0f
)

class WakeFrameProcessor(
    private val gate: NotificationCaptureGate,
    private val engine: WakePhraseEngine,
    private val debouncer: WakeDebouncer = WakeDebouncer()
) {
    fun onPcm(frame: ShortArray, nowMs: Long): WakeFrameResult {
        if (!gate.mayCapture()) return WakeFrameResult(WakeFrameKind.BLOCKED)
        val score = engine.accept(frame)
        if (!score.detected) return WakeFrameResult(WakeFrameKind.LISTENING, score.similarity)
        if (!debouncer.tryAccept(nowMs)) return WakeFrameResult(WakeFrameKind.LISTENING, score.similarity)
        return WakeFrameResult(WakeFrameKind.DETECTED, score.similarity)
    }
}
