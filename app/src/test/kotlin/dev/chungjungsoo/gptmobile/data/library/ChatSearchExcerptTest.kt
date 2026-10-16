package dev.chungjungsoo.gptmobile.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatSearchExcerptTest {
    @Test
    fun escapeLikeQuery_escapesWildcards() {
        assertEquals("\\%\\_price\\\\", escapeLikeQuery("%_price\\"))
    }

    @Test
    fun excerptAround_collapsesWhitespaceAndKeepsAWindow() {
        val content = "Intro line.\n\nThe solar grant is confirmed for the Wexford roof."
        val excerpt = excerptAround(content, "grant", radius = 10)

        assertEquals("…The solar grant is confir…", excerpt)
    }

    @Test
    fun buildSearchExcerpts_keepsNewestHitPerChat() {
        val excerpts = buildSearchExcerpts(
            hits = listOf(
                4 to "newer grant note",
                4 to "older grant note",
                5 to "no match here"
            ),
            query = "grant"
        )

        assertEquals(mapOf(4 to "newer grant note"), excerpts)
        assertNull(excerptAround("nothing", "grant"))
        assertEquals(emptyMap<Int, String>(), buildSearchExcerpts(emptyList(), "  "))
    }
}
