package dev.chungjungsoo.gptmobile.data.project.archive

fun interface ProjectImageProcessor {
    fun process(bytes: ByteArray, mimeType: String, keepOriginal: Boolean): ProcessedImage

    companion object {
        val Identity: ProjectImageProcessor = ProjectImageProcessor { bytes, mimeType, _ ->
            ProcessedImage(bytes = bytes, mimeType = mimeType, downscaled = false)
        }
    }
}

data class ProcessedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val downscaled: Boolean
)
