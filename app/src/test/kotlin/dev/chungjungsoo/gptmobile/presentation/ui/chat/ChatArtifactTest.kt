package dev.chungjungsoo.gptmobile.presentation.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatArtifactTest {
    @Test
    fun extractChatArtifacts_keepsPreviewableFencesAndSkipsCode() {
        val markdown = """
            A page and a diagram.

            ```html
            <h1>Hello</h1>
            <script>alert(1)</script>
            ```

            ```kotlin
            val formula = "${'$'}${'$'}not an artifact${'$'}${'$'}"
            ```

            ```svg
            <svg onclick="alert(1)"></svg>
            ```

            ```xml
            <svg viewBox="0 0 10 10"></svg>
            ```
        """.trimIndent()

        val artifacts = extractChatArtifacts(markdown)

        assertEquals(listOf(ArtifactKind.Html, ArtifactKind.Svg, ArtifactKind.Svg), artifacts.map { it.kind })
        assertEquals("HTML", artifacts[0].title)
        assertEquals("SVG 1", artifacts[1].title)
        assertEquals("SVG 2", artifacts[2].title)
    }

    @Test
    fun sanitizePreviewMarkup_stripsScriptsHandlersAndFrames() {
        val sanitized = sanitizePreviewMarkup(
            """<h1 onclick="alert(1)">Hi</h1><script>alert(1)</script><iframe src="https://evil.test"></iframe><a href="javascript:alert(1)">x</a>"""
        )
        val document = artifactPreviewDocument(
            ChatArtifact(title = "HTML", language = "html", source = sanitized, kind = ArtifactKind.Html)
        )

        assertFalse(sanitized.contains("script", ignoreCase = true))
        assertFalse(sanitized.contains("onclick"))
        assertFalse(sanitized.contains("iframe"))
        assertFalse(sanitized.contains("javascript"))
        assertTrue(document.contains("<h1>Hi</h1>"))
        assertTrue(document.contains("<!DOCTYPE html>"))
    }

    @Test
    fun sanitizePreviewMarkup_blocksNetworkAndIntentEscapes() {
        val sanitized = sanitizePreviewMarkup(
            """
            <img src="https://evil.test/pixel">
            <meta http-equiv="refresh" content="0;url=intent://evil.test">
            <base href="https://evil.test/">
            <a href="javascript&#58;alert(1)">x</a>
            <svg><image href="file:///data/secret"></image></svg>
            """.trimIndent()
        )
        val document = artifactPreviewDocument(
            ChatArtifact(title = "HTML", language = "html", source = "<h1>Hi</h1>", kind = ArtifactKind.Html)
        )

        assertFalse(sanitized.contains("https://evil.test"))
        assertFalse(sanitized.contains("intent:"))
        assertFalse(sanitized.contains("javascript"))
        assertFalse(sanitized.contains("file:"))
        assertFalse(artifactResourceAllowed("https://evil.test/pixel"))
        assertFalse(artifactResourceAllowed("file:///data/secret"))
        assertFalse(artifactResourceAllowed("intent://evil.test"))
        assertFalse(artifactResourceAllowed("javascript:alert(1)"))
        assertTrue(artifactResourceAllowed("about:blank"))
        assertTrue(artifactResourceAllowed("data:image/png;base64,aaaa"))
        assertTrue(document.contains("Content-Security-Policy"))
        assertTrue(document.contains("default-src 'none'"))
    }

    @Test
    fun extractChatArtifacts_ignoresUnclosedAndMarkdownFencesAreKept() {
        assertTrue(extractChatArtifacts("```html\n<h1>Open").isEmpty())
        val markdown = extractChatArtifacts("```md\n# Notes\n```")
        assertEquals(ArtifactKind.Markdown, markdown.single().kind)
        assertEquals("# Notes", markdown.single().source)
    }
}
