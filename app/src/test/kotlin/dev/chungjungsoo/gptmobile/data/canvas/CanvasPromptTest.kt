package dev.chungjungsoo.gptmobile.data.canvas

import dev.chungjungsoo.gptmobile.data.instruction.ChatCompletionContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasPromptTest {
    @Test
    fun canvasInstructions_closedChat_staysEmptyUntilTheUserAsks() {
        assertNull(canvasInstructions(document = null, selection = null, userAskedToOpen = false))
        assertFalse(userAskedForCanvas("Tell me about a painting."))
        assertTrue(userAskedForCanvas("Open a canvas for this plan."))

        val opened = canvasInstructions(document = null, selection = null, userAskedToOpen = true).orEmpty()

        assertTrue(opened.contains("robin-canvas"))
        assertTrue(opened.contains("action: open"))
        assertFalse(opened.contains("WebView"))
    }

    @Test
    fun canvasInstructions_openDocument_includesIdAndSelection() {
        val document = sampleDocument()
        val selection = PendingSelection(start = 0, end = 5, text = "alpha")
        val instructions = canvasInstructions(document, selection, userAskedToOpen = false).orEmpty()

        assertTrue(instructions.contains("id: doc"))
        assertTrue(instructions.contains("propose-selection"))
        assertTrue(instructions.contains("alpha"))
        assertTrue(instructions.contains("<<<CANVAS"))
    }

    @Test
    fun mergeCanvasInstructions_withoutExtra_returnsTheSameContext() {
        val base = ChatCompletionContext(chatId = 4, chatInstructions = "Be brief.")

        assertSame(base, mergeCanvasInstructions(base, null))
        assertSame(base, mergeCanvasInstructions(base, "  "))
        assertEquals(
            "Be brief.\n\nUse the canvas.",
            mergeCanvasInstructions(base, "Use the canvas.").chatInstructions
        )
    }

    private fun sampleDocument() = CanvasDocument(
        id = "doc",
        chatRoomId = 4,
        title = "Notes",
        kind = CanvasKind.Markdown,
        language = "markdown",
        updatedAt = 1,
        versions = listOf(
            CanvasVersion(
                id = "v1",
                content = "alpha beta",
                title = "Notes",
                createdAt = 1,
                source = CanvasVersionSource.Open
            )
        )
    )
}
