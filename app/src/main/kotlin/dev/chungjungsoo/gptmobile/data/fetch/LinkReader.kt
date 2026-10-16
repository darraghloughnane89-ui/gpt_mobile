package dev.chungjungsoo.gptmobile.data.fetch

import dev.chungjungsoo.gptmobile.data.agent.tool.SafePageFetch
import dev.chungjungsoo.gptmobile.data.search.ReadabilityExtractor
import javax.inject.Inject

enum class LinkKind {
    PAGE,
    YOUTUBE
}

data class ReadLink(
    val url: String,
    val finalUrl: String,
    val title: String,
    val text: String,
    val kind: LinkKind
)

class LinkReadException(message: String) : Exception(message)

/**
 * Reads a pasted or shared link. HTML and YouTube captions both go through [SafePageFetch],
 * the same SSRF-pinned client web search uses for `fetch_page`.
 */
class LinkReader internal constructor(
    private val exchange: suspend (url: String, method: String, headers: Map<String, String>, body: String?) -> String
) {
    @Inject constructor() : this(
        exchange = { url, method, headers, body ->
            if (method == "GET" && headers.isEmpty() && body == null) {
                SafePageFetch.fetch(url)
            } else {
                SafePageFetch.fetch(
                    start = url,
                    method = method,
                    headers = headers,
                    body = body,
                    allowAddress = { false }
                )
            }
        }
    )

    suspend fun read(rawUrl: String): ReadLink {
        val url = LinkUrls.firstHttpUrl(rawUrl) ?: throw LinkReadException("unsupported URL")
        val videoId = LinkUrls.youtubeVideoId(url)
        return if (videoId == null) readPage(url) else readYouTube(url, videoId)
    }

    private suspend fun readPage(url: String): ReadLink {
        val html = exchange(url, "GET", emptyMap(), null)
        val text = ReadabilityExtractor.extract(html)
        if (text.isBlank()) throw LinkReadException("no readable text")
        val title = ReadabilityExtractor.title(html).ifBlank { url }
        return ReadLink(
            url = url,
            finalUrl = url,
            title = title,
            text = text,
            kind = LinkKind.PAGE
        )
    }

    private suspend fun readYouTube(url: String, videoId: String): ReadLink {
        val playerRequest = YouTubeTranscript.playerRequest(videoId)
        val player = exchange(playerRequest.url, playerRequest.method, playerRequest.headers, playerRequest.body)
        val track = YouTubeTranscript.selectTrack(player) ?: throw LinkReadException("no public captions")
        val captionRequest = YouTubeTranscript.captionRequest(track.baseUrl)
        val captions = exchange(captionRequest.url, captionRequest.method, captionRequest.headers, captionRequest.body)
        val text = YouTubeTranscript.parseCaptions(captions)
        if (text.isBlank()) throw LinkReadException("no public captions")
        val title = YouTubeTranscript.title(player).ifBlank { "YouTube" }
        return ReadLink(
            url = url,
            finalUrl = url,
            title = title,
            text = text,
            kind = LinkKind.YOUTUBE
        )
    }
}
