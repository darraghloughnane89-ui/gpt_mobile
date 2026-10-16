package dev.chungjungsoo.gptmobile.data.search

object ReadabilityExtractor {
    private val droppedBlocks = listOf("script", "style", "noscript", "svg", "iframe", "nav", "footer", "header", "form", "aside")
    private val titlePattern = Regex("(?is)<title[^>]*>(.*?)</title>")
    private val entityPattern = Regex("&#(x?[0-9a-fA-F]+);")

    fun title(html: String): String = titlePattern.find(html)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { decode(stripTags(it)) }
        ?.trim()
        .orEmpty()

    fun extract(html: String, maxChars: Int = MAX_CHARS): String {
        var working = html
        droppedBlocks.forEach { tag ->
            working = Regex("(?is)<$tag\\b[^>]*>.*?</$tag\\s*>").replace(working, " ")
        }
        val focused = inner(working, "article") ?: inner(working, "main") ?: working
        val withBreaks = focused
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p\\s*>"), "\n")
            .replace(Regex("(?i)</h[1-6]\\s*>"), "\n")
            .replace(Regex("(?i)</li\\s*>"), "\n")
        return collapse(decode(stripTags(withBreaks))).take(maxChars)
    }

    private fun inner(html: String, tag: String): String? {
        val body = Regex("(?is)<$tag\\b[^>]*>(.*?)</$tag\\s*>").find(html)?.groupValues?.getOrNull(1) ?: return null
        return body.takeIf { stripTags(it).length > 40 }
    }

    private fun stripTags(html: String): String = html.replace(Regex("(?is)<[^>]+>"), " ")

    private fun decode(text: String): String {
        val named = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        return entityPattern.replace(named) { match ->
            val raw = match.groupValues[1]
            val code = if (raw.startsWith("x", ignoreCase = true)) {
                raw.drop(1).toIntOrNull(16)
            } else {
                raw.toIntOrNull()
            }
            code?.takeIf { it in 0..0x10FFFF }?.let { Character.toChars(it).concatToString() } ?: match.value
        }
    }

    private fun collapse(text: String): String = text
        .lines()
        .joinToString("\n") { line -> line.replace(Regex("[\\t ]+"), " ").trim() }
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private const val MAX_CHARS = 20_000
}
