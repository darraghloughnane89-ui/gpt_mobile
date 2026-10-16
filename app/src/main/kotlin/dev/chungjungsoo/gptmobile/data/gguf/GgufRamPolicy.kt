package dev.chungjungsoo.gptmobile.data.gguf

enum class RamFit {
    OK,
    WARN,
    REFUSE
}

data class RamAssessment(
    val fit: RamFit,
    val recommendedContext: Int,
    val detail: String?
)

object GgufRamPolicy {
    const val DEFAULT_CONTEXT = 4096
    const val MIN_CONTEXT = 512
    const val MAX_CONTEXT = 32768
    private const val LOW_RAM_BYTES = 3_500_000_000L
    private const val MID_RAM_BYTES = 6_000_000_000L

    fun assess(fileBytes: Long, totalMemBytes: Long): RamAssessment {
        val context = recommendedContext(totalMemBytes)
        if (totalMemBytes <= 0L) {
            return RamAssessment(RamFit.WARN, context, "Device memory could not be read. Using a small context.")
        }
        val refuseAt = totalMemBytes * 55 / 100
        val warnAt = totalMemBytes * 35 / 100
        return when {
            fileBytes > refuseAt -> RamAssessment(
                RamFit.REFUSE,
                context,
                "This GGUF is larger than the memory this device can give it. Choose a smaller quant."
            )

            fileBytes > warnAt -> RamAssessment(
                RamFit.WARN,
                context,
                "This GGUF is large for this device. Generation may be slow, and Android may close the app."
            )

            else -> RamAssessment(RamFit.OK, context, null)
        }
    }

    fun recommendedContext(totalMemBytes: Long): Int = when {
        totalMemBytes <= 0L -> 2048
        totalMemBytes < LOW_RAM_BYTES -> 2048
        totalMemBytes < MID_RAM_BYTES -> DEFAULT_CONTEXT
        else -> 8192
    }

    fun clampContext(requested: Int, totalMemBytes: Long): Int {
        val cap = when {
            totalMemBytes <= 0L -> DEFAULT_CONTEXT
            totalMemBytes < LOW_RAM_BYTES -> DEFAULT_CONTEXT
            totalMemBytes < MID_RAM_BYTES -> 8192
            else -> MAX_CONTEXT
        }
        return requested.coerceIn(MIN_CONTEXT, cap)
    }
}
