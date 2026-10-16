package dev.chungjungsoo.gptmobile.data.phone

object AttachmentNames {
    fun sanitize(fileName: String, fallback: String): String {
        val withoutTraversal = fileName
            .replace("..", "")
            .replace("/", "")
            .replace("\\", "")
        val sanitized = withoutTraversal
            .filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
            .take(MAX_LENGTH)
            .trim('.')
        return sanitized.ifEmpty { fallback }
    }

    private const val MAX_LENGTH = 200
}
