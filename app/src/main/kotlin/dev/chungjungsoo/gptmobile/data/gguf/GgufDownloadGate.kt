package dev.chungjungsoo.gptmobile.data.gguf

sealed class GgufDownloadDecision {
    data object Allowed : GgufDownloadDecision()
    data class Confirm(val warning: String) : GgufDownloadDecision()
    data class Blocked(val message: String) : GgufDownloadDecision()
}

object GgufDownloadMessages {
    const val NOT_A_GGUF_URL = "Paste a Hugging Face link to a .gguf file."
    const val SIZE_UNPUBLISHED = "Hugging Face did not publish a file size for that URL, so the download was not started."
    const val ALREADY_INSTALLED = "That model is already on this device."
    const val ALREADY_DOWNLOADING = "That download is already in progress."
    const val STORAGE = "Not enough free storage. This model needs the remaining file size plus 64 MB of spare space."
    const val CHECKSUM = "The download did not match the published SHA-256. The file was deleted."
    const val NOT_GGUF = "That file is not a GGUF model."
    const val NOT_ON_DEVICE = "That model is not on this device."
}

object GgufDownloadGate {
    const val STORAGE_MARGIN_BYTES = 64L * 1024L * 1024L

    fun evaluate(
        fileBytes: Long,
        availableStorageBytes: Long,
        totalMemBytes: Long,
        partialBytes: Long = 0L,
        storageMarginBytes: Long = STORAGE_MARGIN_BYTES,
        acceptWarning: Boolean = false
    ): GgufDownloadDecision {
        if (fileBytes <= 0L) {
            return GgufDownloadDecision.Blocked(GgufDownloadMessages.SIZE_UNPUBLISHED)
        }
        val remaining = (fileBytes - partialBytes.coerceAtLeast(0L)).coerceAtLeast(0L)
        val needed = if (remaining == 0L) 0L else remaining + storageMarginBytes.coerceAtLeast(0L)
        if (availableStorageBytes < needed) {
            return GgufDownloadDecision.Blocked(GgufDownloadMessages.STORAGE)
        }
        val assessment = GgufRamPolicy.assess(fileBytes, totalMemBytes)
        return when (assessment.fit) {
            RamFit.REFUSE -> GgufDownloadDecision.Blocked(
                assessment.detail ?: "Model is too large for this device"
            )

            RamFit.WARN -> if (acceptWarning) {
                GgufDownloadDecision.Allowed
            } else {
                GgufDownloadDecision.Confirm(
                    assessment.detail ?: "This GGUF is large for this device."
                )
            }

            RamFit.OK -> GgufDownloadDecision.Allowed
        }
    }
}
