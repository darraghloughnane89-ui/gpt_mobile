package dev.chungjungsoo.gptmobile.data.project

enum class ProjectFileKind {
    TEXT,
    PDF,
    IMAGE
}

object ProjectFileTypes {
    fun classify(mimeType: String?, displayName: String): ProjectFileKind? {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return when {
            mime == "application/pdf" || extension == "pdf" -> ProjectFileKind.PDF
            mime.startsWith("image/") || extension in IMAGE_EXTENSIONS -> ProjectFileKind.IMAGE
            mime.startsWith("text/") || extension in TEXT_EXTENSIONS -> ProjectFileKind.TEXT
            else -> null
        }
    }

    private val TEXT_EXTENSIONS = setOf("txt", "md", "markdown", "text", "csv", "json", "log")
    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif")
}
