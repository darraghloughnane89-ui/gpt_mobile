package dev.chungjungsoo.gptmobile.data.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanvasDiffTest {
    @Test
    fun diffLines_insertAndDelete_marksChangedLines() {
        val lines = diffLines("alpha\nbeta\ngamma", "alpha\nBETA\ngamma\ndelta")

        assertEquals(
            listOf(
                CanvasDiffKind.Same,
                CanvasDiffKind.Removed,
                CanvasDiffKind.Added,
                CanvasDiffKind.Same,
                CanvasDiffKind.Added
            ),
            lines.map { it.kind }
        )
        assertEquals(listOf("alpha", "beta", "BETA", "gamma", "delta"), lines.map { it.text })
    }

    @Test
    fun spliceSelection_replacesTheSelectedPassage() {
        val content = "Keep this. Rewrite me. Keep the end."
        val selection = PendingSelection(start = 11, end = 22, text = "Rewrite me.")

        assertEquals(
            "Keep this. Done. Keep the end.",
            spliceSelection(content, selection, "Done.")
        )
    }

    @Test
    fun spliceSelection_ambiguousText_returnsNull() {
        val selection = PendingSelection(start = 0, end = 3, text = "cat")

        assertNull(spliceSelection("cat and cat", selection.copy(start = 99, end = 102), "dog"))
    }
}
