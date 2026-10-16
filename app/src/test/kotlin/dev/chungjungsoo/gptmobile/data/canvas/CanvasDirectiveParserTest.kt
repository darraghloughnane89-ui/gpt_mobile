package dev.chungjungsoo.gptmobile.data.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasDirectiveParserTest {
    @Test
    fun extractCanvasDirectives_openFence_readsHeadersAndBody() {
        val message = """
            Here is the note.

            ```robin-canvas
            title: Trip notes
            kind: markdown
            language: markdown
            action: open
            ---
            # Day 1
            Pack a coat
            ```
        """.trimIndent()

        val directive = extractCanvasDirectives(message).single()

        assertEquals("Trip notes", directive.title)
        assertEquals(CanvasKind.Markdown, directive.kind)
        assertEquals("markdown", directive.language)
        assertEquals(CanvasAction.Open, directive.action)
        assertEquals("# Day 1\nPack a coat", directive.body)
        assertEquals("Here is the note.", stripCanvasFences(message))
    }

    @Test
    fun extractCanvasDirectives_unclosedFence_isIgnored() {
        val message = "```robin-canvas\ntitle: Half\n---\nHello"

        assertTrue(extractCanvasDirectives(message).isEmpty())
        assertEquals(message, stripCanvasFences(message))
    }

    @Test
    fun extractCanvasDirectives_proposeSelection_keepsReplacementOnly() {
        val message = """
            ```robin-canvas
            id: doc-1
            action: propose-selection
            ---
            shorter line
            ```
        """.trimIndent()

        val directive = extractCanvasDirectives(message).single()

        assertEquals("doc-1", directive.id)
        assertEquals(CanvasAction.ProposeSelection, directive.action)
        assertEquals("shorter line", directive.body)
    }

    @Test
    fun extractCanvasDirectives_bodyWithoutDivider_staysOpenDocument() {
        val message = """
            ```robin-canvas
            # Hello
            ```
        """.trimIndent()

        val directive = extractCanvasDirectives(message).single()

        assertNull(directive.id)
        assertEquals(CanvasAction.Open, directive.action)
        assertEquals("# Hello", directive.body)
    }

    @Test
    fun canvasDisplayText_fenceOnly_usesTitle() {
        val message = """
            ```robin-canvas
            title: Notes
            ---
            Body
            ```
        """.trimIndent()

        assertEquals("Notes", canvasDisplayText(message))
    }
}
