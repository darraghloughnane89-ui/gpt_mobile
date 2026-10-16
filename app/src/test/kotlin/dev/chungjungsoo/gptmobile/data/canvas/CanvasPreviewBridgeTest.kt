package dev.chungjungsoo.gptmobile.data.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasPreviewBridgeTest {
    @Test
    fun canvasSeedFromPreviewArtifact_htmlAndSvg_stayWithArtifactPreview() {
        assertNull(canvasSeedFromPreviewArtifact("HTML", "html", "<p>Hi</p>", "Html"))
        assertNull(canvasSeedFromPreviewArtifact("SVG", "svg", "<svg></svg>", "Svg"))
        assertNull(canvasSeedFromPreviewArtifact("SVG", "xml", "<svg viewBox=\"0 0 1 1\"></svg>", "Svg"))
    }

    @Test
    fun canvasSeedFromPreviewArtifact_markdown_becomesEditableSeed() {
        val seed = canvasSeedFromPreviewArtifact("Notes", "markdown", "# Hi", "Markdown")

        assertEquals(CanvasKind.Markdown, seed?.kind)
        assertEquals("# Hi", seed?.content)
        assertEquals("Notes", seed?.title)
    }

    @Test
    fun editableCanvasSeeds_skipsHtmlAndKeepsMarkdownAndCode() {
        val markdown = """
            ```html
            <script>alert(1)</script>
            ```

            ```markdown
            # Plan
            ```

            ```kotlin
            fun main() {}
            ```

            ```svg
            <svg></svg>
            ```
        """.trimIndent()

        val seeds = editableCanvasSeeds(markdown)

        assertEquals(listOf("markdown", "kotlin"), seeds.map { it.language })
        assertEquals(CanvasKind.Markdown, seeds[0].kind)
        assertEquals(CanvasKind.Code, seeds[1].kind)
    }

    @Test
    fun planAssistantOpen_htmlOnly_isPreviewOwned() {
        val plan = planAssistantOpen("```html\n<p>Hi</p>\n```")

        assertTrue(plan is AssistantCanvasPlan.PreviewOwned)
    }

    @Test
    fun planAssistantOpen_markdownFence_isSeed() {
        val plan = planAssistantOpen("```markdown\n# Hi\n```")

        val seed = (plan as AssistantCanvasPlan.Seed).seed
        assertEquals("# Hi", seed.content)
        assertEquals(CanvasKind.Markdown, seed.kind)
    }
}
