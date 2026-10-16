package dev.chungjungsoo.gptmobile.data.fetch

import dev.chungjungsoo.gptmobile.data.agent.tool.SafePageFetch
import java.net.URI

object LinkUrls {
    private val URL_IN_TEXT = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private val TRAILING_PUNCTUATION = Regex("""[.,);:\]]+$""")
    private val VIDEO_ID = Regex("""^[A-Za-z0-9_-]{11}$""")

    fun firstHttpUrl(text: String): String? = URL_IN_TEXT.findAll(text)
        .map { match -> match.value.replace(TRAILING_PUNCTUATION, "").substringBefore('#') }
        .firstOrNull { candidate -> SafePageFetch.parse(candidate) != null }

    fun onlyUrl(text: String): String? {
        val trimmed = text.trim()
        val url = firstHttpUrl(trimmed) ?: return null
        return url.takeIf { trimmed == url }
    }

    fun youtubeVideoId(url: String): String? {
        val uri = SafePageFetch.parse(url) ?: return null
        val host = uri.host?.lowercase().orEmpty().removePrefix("www.")
        val path = uri.path.orEmpty()
        val id = when {
            host == "youtu.be" -> path.trim('/').substringBefore('/')
            host == "youtube.com" || host == "m.youtube.com" || host == "music.youtube.com" -> when {
                path.startsWith("/shorts/") -> path.removePrefix("/shorts/").substringBefore('/')
                path.startsWith("/embed/") -> path.removePrefix("/embed/").substringBefore('/')
                path.startsWith("/live/") -> path.removePrefix("/live/").substringBefore('/')
                path == "/watch" || path.startsWith("/watch/") -> queryParam(uri, "v")
                else -> null
            }
            else -> null
        }
        return id?.takeIf { VIDEO_ID.matches(it) }
    }

    private fun queryParam(uri: URI, name: String): String? = uri.rawQuery
        ?.split('&')
        ?.mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size == 2 && pieces[0] == name) pieces[1] else null
        }
        ?.firstOrNull()
        ?.takeIf { it.isNotBlank() }
}
