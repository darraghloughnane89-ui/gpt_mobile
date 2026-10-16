package dev.chungjungsoo.gptmobile.data.fetch

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkReaderTest {

    @Test
    fun read_htmlPage_usesSharedReadability() = runBlocking {
        val reader = reader(html = ARTICLE)

        val link = reader.read("https://example.com/garden")

        assertEquals(LinkKind.PAGE, link.kind)
        assertEquals("Garden notes", link.title)
        assertTrue(link.text.contains("Beans went in on Tuesday."))
    }

    @Test
    fun read_youTubeUrl_usesPublicCaptionTrack() = runBlocking {
        val reader = reader(
            player = PLAYER,
            captions = """{"events":[{"segs":[{"utf8":"the coolest thing"}]}]}"""
        )

        val link = reader.read("https://www.youtube.com/watch?v=jNQXAC9IVRw")

        assertEquals(LinkKind.YOUTUBE, link.kind)
        assertEquals("Me at the zoo", link.title)
        assertEquals("the coolest thing", link.text)
    }

    private fun reader(html: String = "", player: String = "", captions: String = "") = LinkReader { url, _, _, _ ->
        when {
            url.contains("youtubei/v1/player") -> player
            url.contains("timedtext") -> captions
            else -> html
        }
    }

    private companion object {
        const val ARTICLE = """
            <html><head><title>Garden notes</title></head>
            <body><article><p>Beans went in on Tuesday.</p></article></body></html>
        """
        const val PLAYER = """
            {"videoDetails":{"title":"Me at the zoo"},"captions":{"playerCaptionsTracklistRenderer":{"captionTracks":[
            {"baseUrl":"https://www.youtube.com/api/timedtext?v=jNQXAC9IVRw&lang=en","languageCode":"en"}
            ]}}}
        """
    }
}
