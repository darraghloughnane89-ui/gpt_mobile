package dev.chungjungsoo.gptmobile.data.phone

/**
 * Decides how a shared PDF becomes composer text and attachments.
 * Text the extractor can read is placed in the composer. A blank extract
 * asks the caller to render a few pages through the existing image path.
 */
object PdfSharePlanner {
    const val MAX_RENDERED_PAGES = 3

    sealed interface PdfPlan {
        data class Text(val composerSection: String) : PdfPlan
        data object NeedsPageImages : PdfPlan
    }

    fun plan(fileName: String, extractedText: String): PdfPlan {
        val trimmed = extractedText.trim()
        if (trimmed.isEmpty()) return PdfPlan.NeedsPageImages
        return PdfPlan.Text(composerSection(fileName, trimmed))
    }

    fun composerSection(fileName: String, text: String): String = "PDF: $fileName\n$text"

    fun pageImageNote(fileName: String): String = "Attached page images from $fileName."

    fun compose(sharedText: String, sections: List<String>, maxChars: Int = PdfTextExtractor.MAX_CHARS): String =
        buildList {
            if (sharedText.isNotBlank()) add(sharedText.trim())
            sections.filter { it.isNotBlank() }.forEach(::add)
        }.joinToString("\n\n").take(maxChars)
}
