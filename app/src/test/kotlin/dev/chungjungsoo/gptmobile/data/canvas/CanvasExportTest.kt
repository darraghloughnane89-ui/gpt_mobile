package dev.chungjungsoo.gptmobile.data.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasExportTest {
    @Test
    fun exportCanvasText_code_isFencedMarkdownAndPlainStaysRaw() {
        val document = sample(CanvasKind.Code, "kotlin", "fun main() {}")

        assertEquals("fun main() {}", exportCanvasText(document, CanvasExportFormat.PlainText))
        assertEquals("```kotlin\nfun main() {}\n```\n", exportCanvasText(document, CanvasExportFormat.Markdown))
        assertEquals("notes.md", exportCanvasFileName("Notes", CanvasExportFormat.Markdown))
        assertEquals("notes.txt", exportCanvasFileName("Notes", CanvasExportFormat.PlainText))
        assertEquals("notes.pdf", exportCanvasFileName("Notes!", CanvasExportFormat.Pdf))
    }

    @Test
    fun paginateForPdf_wrapsLongLinesAndSplitsPages() {
        val text = "word ".repeat(40).trim() + "\n" + "short"
        val pages = paginateForPdf(text, columns = 20, linesPerPage = 2)

        assertTrue(pages.first().all { it.length <= 20 })
        assertTrue(pages.size > 1)
        assertEquals("short", pages.last().last())
    }

    private fun sample(kind: CanvasKind, language: String, content: String) = CanvasDocument(
        id = "doc",
        chatRoomId = 1,
        title = "Notes",
        kind = kind,
        language = language,
        updatedAt = 1,
        versions = listOf(
            CanvasVersion(
                id = "v",
                content = content,
                title = "Notes",
                createdAt = 1,
                source = CanvasVersionSource.User
            )
        )
    )
}
