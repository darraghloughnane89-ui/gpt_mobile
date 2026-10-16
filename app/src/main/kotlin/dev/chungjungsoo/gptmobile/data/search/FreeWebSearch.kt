package dev.chungjungsoo.gptmobile.data.search

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String
)

object DuckDuckGoSearch {
    private val anchor = Regex("(?is)<a\\b([^>]*)>(.*?)</a>")

    fun parse(html: String, limit: Int): List<SearchHit> {
        val anchors = anchor.findAll(html).map { match ->
            val attributes = match.groupValues[1]
            Triple(
                attribute(attributes, "class"),
                attribute(attributes, "href"),
                ReadabilityExtractor.extract("<article>${match.groupValues[2]}</article>", maxChars = 300)
            )
        }.toList()
        val links = anchors.filter { it.first.contains("result__a") }
        val snippets = anchors.filter { it.first.contains("result__snippet") }
        return links.mapIndexedNotNull { index, link ->
            val url = unwrap(link.second)
            if (!url.startsWith("http://") && !url.startsWith("https://")) return@mapIndexedNotNull null
            SearchHit(
                title = link.third.ifBlank { "Result ${index + 1}" },
                url = url,
                snippet = snippets.getOrNull(index)?.third.orEmpty()
            )
        }.take(limit.coerceIn(1, MAX_RESULTS))
    }

    fun searchUrl(query: String): String = "https://html.duckduckgo.com/html/?q=" + encode(query)

    internal fun unwrap(href: String): String {
        val decoded = decodeBasic(href.trim())
        val absolute = if (decoded.startsWith("//")) "https:$decoded" else decoded
        val uri = runCatching { URI(absolute) }.getOrNull() ?: return absolute
        val uddg = uri.rawQuery
            ?.split("&")
            ?.firstOrNull { it.startsWith("uddg=") }
            ?.removePrefix("uddg=")
            ?: return absolute
        return runCatching { URLDecoder.decode(uddg, StandardCharsets.UTF_8) }.getOrDefault(absolute)
    }

    private fun attribute(attributes: String, name: String): String {
        val match = Regex("""(?i)\b$name\s*=\s*(?:"([^"]*)"|'([^']*)')""").find(attributes) ?: return ""
        return match.groupValues[1].ifBlank { match.groupValues[2] }
    }

    private fun decodeBasic(value: String): String = value
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
}

object SearxngSearch {
    private val json = Json { ignoreUnknownKeys = true }

    fun searchUrl(base: String, query: String): String? {
        val trimmed = base.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        val root = if (trimmed.endsWith("/search")) trimmed else "$trimmed/search"
        return "$root?q=${encode(query)}&format=json"
    }

    fun parse(body: String, limit: Int): List<SearchHit> {
        val results = runCatching { json.parseToJsonElement(body).jsonObject["results"]?.jsonArray }.getOrNull()
            ?: return emptyList()
        return results.mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (!url.startsWith("http://") && !url.startsWith("https://")) return@mapNotNull null
            SearchHit(
                title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { url },
                url = url,
                snippet = obj["content"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            )
        }.take(limit.coerceIn(1, MAX_RESULTS))
    }
}

class FreeWebSearchClient(
    private val searxngUrl: String?,
    private val fetch: suspend (String) -> String = ::httpGetText
) {
    suspend fun search(query: String, limit: Int): List<SearchHit> {
        val bounded = limit.coerceIn(1, MAX_RESULTS)
        val searxng = searxngUrl?.let { SearxngSearch.searchUrl(it, query) }
        if (searxng != null) {
            val hits = runCatching { SearxngSearch.parse(fetch(searxng), bounded) }.getOrNull()
            if (hits != null) return hits
        }
        return DuckDuckGoSearch.parse(fetch(DuckDuckGoSearch.searchUrl(query)), bounded)
    }
}

internal suspend fun httpGetText(url: String, maxBytes: Int = MAX_FETCH_BYTES): String {
    val client = HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
                followSslRedirects(true)
            }
        }
    }
    try {
        val response = client.get(url) {
            header(HttpHeaders.UserAgent, SEARCH_USER_AGENT)
            header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/json")
        }
        val channel = response.bodyAsChannel()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read == -1) break
            if (read == 0) {
                yield()
                continue
            }
            output.write(buffer, 0, read)
            if (output.size() > maxBytes) break
        }
        return output.toByteArray().toString(StandardCharsets.UTF_8)
    } finally {
        client.close()
    }
}

private fun encode(query: String): String = URLEncoder.encode(query, StandardCharsets.UTF_8)

private const val MAX_RESULTS = 8
private const val MAX_FETCH_BYTES = 1024 * 1024
private const val SEARCH_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
