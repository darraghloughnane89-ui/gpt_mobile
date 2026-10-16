package dev.chungjungsoo.gptmobile.data.search

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

object CitationExtractors {
    private val numberedLine = Regex("""^\[(\d+)]\s+(.+)$""")
    private val urlLine = Regex("""^URL:\s*(\S+)$""", RegexOption.IGNORE_CASE)

    fun number(citations: List<WebCitation>): List<WebCitation> {
        val seen = linkedSetOf<String>()
        val unique = citations.mapNotNull { citation ->
            val url = citation.url.trim()
            if (!isHttp(url) || !seen.add(url)) return@mapNotNull null
            citation.copy(url = url, title = citation.title.ifBlank { url })
        }
        return unique.mapIndexed { index, citation -> citation.copy(number = index + 1) }
    }

    fun fromResponses(output: JsonArray?, citations: JsonArray?): List<WebCitation> {
        val found = mutableListOf<WebCitation>()
        citations.orEmpty().forEach { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull?.let { url ->
                    found += WebCitation(0, url, url)
                }

                is JsonObject -> citationFromObject(element)?.let { found += it }

                else -> Unit
            }
        }
        output.orEmpty().forEach { item ->
            val content = (item as? JsonObject)?.array("content") ?: return@forEach
            content.forEach { part ->
                val partObject = part as? JsonObject ?: return@forEach
                partObject.array("annotations").orEmpty().forEach { annotation ->
                    val annotationObject = annotation as? JsonObject ?: return@forEach
                    val type = annotationObject.string("type")
                    if (type != null && type != "url_citation") return@forEach
                    citationFromObject(annotationObject)?.let { found += it }
                }
            }
        }
        return number(found)
    }

    fun fromAnthropicCitation(obj: JsonObject): WebCitation? {
        val type = obj.string("type")
        if (type != null && type != "web_search_result_location" && type != "web_search_result") return null
        return citationFromObject(obj)
    }

    fun fromGrounding(metadata: JsonObject): List<WebCitation> {
        val chunks = metadata.array("groundingChunks", "grounding_chunks") ?: return emptyList()
        return number(
            chunks.mapNotNull { chunk ->
                val web = (chunk as? JsonObject)?.get("web") as? JsonObject ?: return@mapNotNull null
                val url = web.string("uri", "url") ?: return@mapNotNull null
                WebCitation(0, web.string("title") ?: url, url)
            }
        )
    }

    fun fromToolText(text: String): List<WebCitation> {
        val found = mutableListOf<WebCitation>()
        var pendingNumber = 0
        var pendingTitle = ""
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            val head = numberedLine.matchEntire(line)
            if (head != null) {
                pendingNumber = head.groupValues[1].toIntOrNull() ?: 0
                pendingTitle = head.groupValues[2].trim()
                return@forEach
            }
            val url = urlLine.matchEntire(line)?.groupValues?.getOrNull(1) ?: return@forEach
            if (pendingTitle.isNotBlank() && isHttp(url)) {
                found += WebCitation(pendingNumber, pendingTitle, url)
            }
            pendingTitle = ""
        }
        return found
    }

    private fun citationFromObject(obj: JsonObject): WebCitation? {
        val url = obj.string("url", "uri") ?: return null
        if (!isHttp(url)) return null
        return WebCitation(0, obj.string("title")?.takeIf { it.isNotBlank() } ?: url, url)
    }

    private fun isHttp(url: String): Boolean {
        val scheme = url.substringBefore(':').lowercase()
        return scheme == "http" || scheme == "https"
    }

    private fun JsonObject.string(vararg names: String): String? {
        names.forEach { name ->
            val value = (get(name) as? JsonPrimitive)?.contentOrNull?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    private fun JsonObject.array(vararg names: String): JsonArray? {
        names.forEach { name ->
            val value = get(name)
            if (value is JsonArray) return value
            runCatching { value?.jsonArray }.getOrNull()?.let { return it }
        }
        return null
    }
}
