package dev.chungjungsoo.gptmobile.data.voice

import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device "Hey Robin" scorer.
 *
 * The audio contract matches openWakeWord (Apache-2.0): 16 kHz mono PCM and 80 ms frames.
 * Scoring uses a log-mel trajectory against templates synthesized in this file. It does not
 * download a model, call the network, or use Picovoice.
 */
class OpenWakeWordEngine @Inject constructor() : WakePhraseEngine {
    override val phrase: String = WakePhrase.SPOKEN
    override val licence: WakeEngineLicence = WakeEngineLicence.OPEN_WAKE_WORD

    private val buffer = ShortArray(SAMPLE_RATE * 2)
    private var stored = 0
    private var latched = false

    override fun reset() {
        stored = 0
        latched = false
    }

    override fun accept(pcm16: ShortArray): WakeScore {
        append(pcm16)
        if (stored < MIN_SAMPLES) return WakeScore(0f, false)
        val similarity = scorePcm(buffer.copyOf(stored))
        val hot = similarity >= THRESHOLD
        val detected = hot && !latched
        if (detected) latched = true
        if (!hot && similarity < THRESHOLD * 0.8f) latched = false
        return WakeScore(similarity, detected)
    }

    fun scorePcm(pcm16: ShortArray): Float {
        val query = embed(pcm16)
        var best = 0f
        for (template in templates) {
            val score = cosine(query, template)
            if (score > best) best = score
        }
        return best
    }

    private fun append(pcm16: ShortArray) {
        if (pcm16.isEmpty()) return
        if (pcm16.size >= buffer.size) {
            pcm16.copyInto(buffer, 0, pcm16.size - buffer.size, pcm16.size)
            stored = buffer.size
            return
        }
        if (stored + pcm16.size > buffer.size) {
            val overflow = stored + pcm16.size - buffer.size
            buffer.copyInto(buffer, 0, overflow, stored)
            stored -= overflow
        }
        pcm16.copyInto(buffer, stored, 0, pcm16.size)
        stored += pcm16.size
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_SAMPLES = 1_280
        const val THRESHOLD = 0.86f
        private const val MIN_SAMPLES = 7_200
        private const val WINDOW = 400
        private const val HOP = 160
        private const val FFT_SIZE = 512
        private const val MELS = 16
        private const val FIXED_FRAMES = 32

        private val hann = FloatArray(WINDOW) { index ->
            (0.5 - 0.5 * cos(2.0 * PI * index / (WINDOW - 1))).toFloat()
        }
        private val melFilters = melFilterbank()
        private val templates: List<FloatArray> = buildTemplates()

        fun featureFrame(pcm16: ShortArray): FloatArray = embed(pcm16)

        private fun buildTemplates(): List<FloatArray> {
            val speeds = floatArrayOf(0.9f, 1f, 1.12f)
            val pitchScales = floatArrayOf(0.92f, 1f, 1.1f)
            return speeds.flatMap { speed ->
                pitchScales.map { pitch ->
                    embed(synthesize(heyRobinRecipe(speed, pitch)))
                }
            }
        }

        private fun embed(pcm16: ShortArray): FloatArray {
            val voiced = trimSilence(logMel(pcm16))
            if (voiced.size < 6) return FloatArray(FIXED_FRAMES * MELS)
            return resample(voiced)
        }

        private fun logMel(pcm16: ShortArray): List<FloatArray> {
            if (pcm16.size < WINDOW) return emptyList()
            val frames = ArrayList<FloatArray>((pcm16.size - WINDOW) / HOP + 1)
            val real = FloatArray(FFT_SIZE)
            val imag = FloatArray(FFT_SIZE)
            var offset = 0
            while (offset + WINDOW <= pcm16.size) {
                real.fill(0f)
                imag.fill(0f)
                for (index in 0 until WINDOW) {
                    real[index] = (pcm16[offset + index] / 32768f) * hann[index]
                }
                fft(real, imag)
                val mel = FloatArray(MELS)
                val bins = FFT_SIZE / 2 + 1
                for (band in 0 until MELS) {
                    var energy = 0f
                    val filter = band * bins
                    for (bin in 0 until bins) {
                        val weight = melFilters[filter + bin]
                        if (weight != 0f) {
                            val power = real[bin] * real[bin] + imag[bin] * imag[bin]
                            energy += weight * power
                        }
                    }
                    mel[band] = ln(energy + 1e-6f)
                }
                frames.add(mel)
                offset += HOP
            }
            return frames
        }

        private fun trimSilence(frames: List<FloatArray>): List<FloatArray> {
            if (frames.isEmpty()) return frames
            var peak = 0f
            val energy = FloatArray(frames.size)
            for (index in frames.indices) {
                var sum = 0f
                for (value in frames[index]) sum += value
                energy[index] = sum / frames[index].size
                if (energy[index] > peak) peak = energy[index]
            }
            val floor = peak - 1.4f
            val start = energy.indexOfFirst { it >= floor }
            val end = energy.indexOfLast { it >= floor }
            if (start < 0 || end - start < 5) return frames
            return frames.subList(start, end + 1)
        }

        private fun resample(frames: List<FloatArray>): FloatArray {
            val output = FloatArray(FIXED_FRAMES * MELS)
            if (frames.isEmpty()) return output
            for (slot in 0 until FIXED_FRAMES) {
                val position = slot * (frames.size - 1).coerceAtLeast(0) / (FIXED_FRAMES - 1).toFloat()
                val left = position.toInt().coerceIn(0, frames.lastIndex)
                val right = (left + 1).coerceAtMost(frames.lastIndex)
                val mix = position - left
                for (band in 0 until MELS) {
                    output[slot * MELS + band] = frames[left][band] * (1f - mix) + frames[right][band] * mix
                }
            }
            var mean = 0f
            for (value in output) mean += value
            mean /= output.size
            var energy = 0f
            for (index in output.indices) {
                output[index] -= mean
                energy += output[index] * output[index]
            }
            val scale = sqrt(energy).coerceAtLeast(1e-4f)
            for (index in output.indices) output[index] /= scale
            return output
        }

        private fun cosine(left: FloatArray, right: FloatArray): Float {
            var dot = 0f
            for (index in left.indices) dot += left[index] * right[index]
            return dot.coerceIn(-1f, 1f)
        }

        private fun melFilterbank(): FloatArray {
            val bins = FFT_SIZE / 2 + 1
            val filters = FloatArray(MELS * bins)
            val low = hzToMel(80f)
            val high = hzToMel(7_000f)
            val points = FloatArray(MELS + 2) { index ->
                melToHz(low + (high - low) * index / (MELS + 1))
            }
            val binForHz = { hz: Float -> (hz * FFT_SIZE / SAMPLE_RATE).toInt().coerceIn(0, bins - 1) }
            for (band in 0 until MELS) {
                val left = binForHz(points[band])
                val center = binForHz(points[band + 1])
                val right = binForHz(points[band + 2])
                for (bin in left until center) {
                    if (center != left) filters[band * bins + bin] = (bin - left).toFloat() / (center - left)
                }
                for (bin in center until right) {
                    if (right != center) filters[band * bins + bin] = (right - bin).toFloat() / (right - center)
                }
            }
            return filters
        }

        private fun hzToMel(hz: Float): Float = (2595.0 * log10(1.0 + hz / 700.0)).toFloat()

        private fun melToHz(mel: Float): Float = (700.0 * (10.0.pow(mel / 2595.0) - 1.0)).toFloat()

        private fun fft(real: FloatArray, imag: FloatArray) {
            val size = real.size
            var reversed = 0
            for (index in 1 until size) {
                var bit = size shr 1
                while (reversed and bit != 0) {
                    reversed = reversed xor bit
                    bit = bit shr 1
                }
                reversed = reversed xor bit
                if (index < reversed) {
                    val swapReal = real[index]
                    real[index] = real[reversed]
                    real[reversed] = swapReal
                    val swapImag = imag[index]
                    imag[index] = imag[reversed]
                    imag[reversed] = swapImag
                }
            }
            var length = 2
            while (length <= size) {
                val angle = -2.0 * PI / length
                val stepCos = cos(angle).toFloat()
                val stepSin = sin(angle).toFloat()
                var start = 0
                while (start < size) {
                    var twiddleCos = 1f
                    var twiddleSin = 0f
                    for (offset in 0 until length / 2) {
                        val even = start + offset
                        val odd = even + length / 2
                        val mixedReal = twiddleCos * real[odd] - twiddleSin * imag[odd]
                        val mixedImag = twiddleCos * imag[odd] + twiddleSin * real[odd]
                        real[odd] = real[even] - mixedReal
                        imag[odd] = imag[even] - mixedImag
                        real[even] += mixedReal
                        imag[even] += mixedImag
                        val nextCos = twiddleCos * stepCos - twiddleSin * stepSin
                        twiddleSin = twiddleCos * stepSin + twiddleSin * stepCos
                        twiddleCos = nextCos
                    }
                    start += length
                }
                length = length shl 1
            }
        }
    }
}

internal data class ToneSegment(
    val durationMs: Int,
    val frequencies: FloatArray,
    val amplitude: Float
)

internal fun heyRobinRecipe(speed: Float, pitchScale: Float): List<ToneSegment> = listOf(
    ToneSegment(scaled(40, speed), floatArrayOf(-1f), 0.2f),
    ToneSegment(scaled(150, speed), floatArrayOf(480f * pitchScale, 2200f * pitchScale), 0.6f),
    ToneSegment(scaled(130, speed), floatArrayOf(560f * pitchScale, 1750f * pitchScale), 0.55f),
    ToneSegment(scaled(70, speed), floatArrayOf(), 0f),
    ToneSegment(scaled(120, speed), floatArrayOf(400f * pitchScale, 1050f * pitchScale, 1550f * pitchScale), 0.55f),
    ToneSegment(scaled(140, speed), floatArrayOf(680f * pitchScale, 980f * pitchScale), 0.6f),
    ToneSegment(scaled(45, speed), floatArrayOf(), 0f),
    ToneSegment(scaled(25, speed), floatArrayOf(-1f), 0.3f),
    ToneSegment(scaled(100, speed), floatArrayOf(390f * pitchScale, 2050f * pitchScale), 0.55f),
    ToneSegment(scaled(110, speed), floatArrayOf(270f * pitchScale, 1350f * pitchScale), 0.4f)
)

internal fun otherPhraseRecipe(speed: Float, pitchScale: Float): List<ToneSegment> = listOf(
    ToneSegment(scaled(160, speed), floatArrayOf(600f * pitchScale, 900f * pitchScale), 0.55f),
    ToneSegment(scaled(140, speed), floatArrayOf(350f * pitchScale, 1400f * pitchScale), 0.5f),
    ToneSegment(scaled(50, speed), floatArrayOf(), 0f),
    ToneSegment(scaled(180, speed), floatArrayOf(500f * pitchScale, 1500f * pitchScale, 2500f * pitchScale), 0.5f),
    ToneSegment(scaled(160, speed), floatArrayOf(800f * pitchScale, 1200f * pitchScale), 0.55f),
    ToneSegment(scaled(140, speed), floatArrayOf(300f * pitchScale, 700f * pitchScale), 0.45f)
)

internal fun synthesize(segments: List<ToneSegment>, sampleRate: Int = OpenWakeWordEngine.SAMPLE_RATE): ShortArray {
    val total = segments.sumOf { (it.durationMs * sampleRate) / 1000 }
    val samples = ShortArray(total.coerceAtLeast(1))
    var cursor = 0
    var noise = 0x1234
    for (segment in segments) {
        val count = (segment.durationMs * sampleRate) / 1000
        for (index in 0 until count) {
            val time = index.toFloat() / sampleRate
            var sample = 0f
            if (segment.frequencies.size == 1 && segment.frequencies[0] < 0f) {
                noise = noise * 1103515245 + 12345
                sample = ((noise shr 16) and 0xffff) / 32768f - 1f
            } else {
                for (frequency in segment.frequencies) {
                    sample += sin(2f * PI.toFloat() * frequency * time)
                }
                if (segment.frequencies.isNotEmpty()) sample /= segment.frequencies.size
            }
            val fade = envelope(index, count)
            val value = (sample * segment.amplitude * fade * 32767f).toInt().coerceIn(-32767, 32767)
            samples[cursor + index] = value.toShort()
        }
        cursor += count
    }
    return samples
}

private fun scaled(durationMs: Int, speed: Float): Int = (durationMs / speed).toInt().coerceAtLeast(10)

private fun envelope(index: Int, count: Int): Float {
    val edge = (count * 0.08f).toInt().coerceAtLeast(1)
    val attack = (index.toFloat() / edge).coerceIn(0f, 1f)
    val release = ((count - 1 - index).toFloat() / edge).coerceIn(0f, 1f)
    return minOf(attack, release)
}

/** Wake engine that delegates the decision to an ONNX (or other) feature runner. */
class OnnxAdoptedWakeEngine(
    private val runner: OnnxWakeFeatureRunner,
    private val featuresOf: (ShortArray) -> FloatArray = OpenWakeWordEngine::featureFrame,
    private val threshold: Float = 0.5f
) : WakePhraseEngine {
    override val phrase: String = WakePhrase.SPOKEN
    override val licence: WakeEngineLicence = WakeEngineLicence.OPEN_WAKE_WORD

    override fun reset() = Unit

    override fun accept(pcm16: ShortArray): WakeScore {
        val probability = runner.score(featuresOf(pcm16)).coerceIn(0f, 1f)
        return WakeScore(probability, probability >= threshold)
    }
}
