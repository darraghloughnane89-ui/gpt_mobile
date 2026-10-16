package dev.chungjungsoo.gptmobile.data.library

fun escapeLikeQuery(query: String): String = buildString(query.length) {
    query.forEach { character ->
        if (character == '\\' || character == '%' || character == '_') append('\\')
        append(character)
    }
}

fun excerptAround(content: String, query: String, radius: Int = 42): String? {
    if (query.isBlank()) return null
    val collapsed = content.replace(Regex("\\s+"), " ").trim()
    val index = collapsed.indexOf(query, ignoreCase = true)
    if (index < 0) return null
    val start = (index - radius).coerceAtLeast(0)
    val end = (index + query.length + radius).coerceAtMost(collapsed.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < collapsed.length) "…" else ""
    return prefix + collapsed.substring(start, end).trim() + suffix
}

fun buildSearchExcerpts(hits: List<Pair<Int, String>>, query: String): Map<Int, String> {
    if (query.isBlank()) return emptyMap()
    val excerpts = linkedMapOf<Int, String>()
    hits.forEach { (chatId, content) ->
        if (chatId in excerpts) return@forEach
        val excerpt = excerptAround(content, query) ?: return@forEach
        excerpts[chatId] = excerpt
    }
    return excerpts
}
