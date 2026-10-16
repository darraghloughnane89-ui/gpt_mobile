package dev.chungjungsoo.gptmobile.data.search

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Citations ride in the assistant text so they survive reload without a Room schema change.
 * The trailer is stripped before markdown, copy, and export.
 */
data class WebCitation(
    val number: Int,
    val title: String,
    val url: String
)

data class ParsedCitations(
    val body: String,
    val citations: List<WebCitation>
)

object CitationMarkup {
    private val json = Json { ignoreUnknownKeys = true }
    private val completeTrailer = Regex("\\n?<!--gptm-citations:(.*?)-->\\s*\\z", RegexOption.DOT_MATCHES_ALL)
    private val incompleteTrailer = Regex("\\n?<!--gptm-citations:.*\\z", RegexOption.DOT_MATCHES_ALL)

    fun parse(content: String): ParsedCitations {
        val match = completeTrailer.find(content)
        if (match != null) {
            val body = content.removeRange(match.range).trimEnd()
            val citations = runCatching {
                json.decodeFromString<List<StoredCitation>>(match.groupValues[1])
            }.getOrDefault(emptyList()).map { stored ->
                WebCitation(stored.n, stored.title, stored.url)
            }
            return ParsedCitations(body, citations)
        }
        val partial = incompleteTrailer.find(content)
        if (partial != null) {
            return ParsedCitations(content.removeRange(partial.range).trimEnd(), emptyList())
        }
        return ParsedCitations(content, emptyList())
    }

    fun append(body: String, citations: List<WebCitation>): String {
        val trailer = trailer(citations)
        if (trailer.isEmpty()) return body
        return body.trimEnd() + trailer
    }

    fun trailer(citations: List<WebCitation>): String {
        if (citations.isEmpty()) return ""
        val payload = json.encodeToString(
            citations.map { citation ->
                StoredCitation(
                    n = citation.number,
                    title = sanitize(citation.title),
                    url = sanitize(citation.url)
                )
            }
        )
        return "\n\n<!--gptm-citations:$payload-->"
    }

    private fun sanitize(value: String): String = value
        .replace("--", "—")
        .replace("<", "")
        .replace(">", "")
        .trim()
        .take(180)
}

@Serializable
private data class StoredCitation(
    val n: Int,
    val title: String,
    val url: String
)
