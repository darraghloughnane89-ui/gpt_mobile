package dev.chungjungsoo.gptmobile.data.voice

class ReplySentenceSplitter {
    private var committed = 0

    fun reset() {
        committed = 0
    }

    fun consume(fullText: String, complete: Boolean): List<String> {
        val speakable = speakableReply(fullText, complete)
        val end = if (complete) speakable.length else lastSpokenBoundary(speakable)
        if (end <= committed) return emptyList()
        val chunk = speakable.substring(committed, end).trim()
        committed = end
        if (chunk.isEmpty()) return emptyList()
        return chunkSentences(chunk)
    }
}

internal fun speakableReply(raw: String, complete: Boolean): String {
    val fenceCount = raw.split("```").size - 1
    val closed = if (!complete && fenceCount % 2 == 1) {
        val cut = raw.lastIndexOf("```")
        if (cut >= 0) raw.substring(0, cut) else raw
    } else {
        raw
    }
    val withoutFences = closed.replace(Regex("```[\\s\\S]*?```"), " ")
    val withoutLinks = withoutFences.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
    return withoutLinks
        .replace(Regex("[*_`#>|]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

internal fun lastSpokenBoundary(text: String): Int {
    var boundary = 0
    for (index in text.indices) {
        val character = text[index]
        if (character != '.' && character != '!' && character != '?' && character != '\n') continue
        val next = text.getOrNull(index + 1)
        if (character == '.' && next != null && next.isDigit()) continue
        if (next == null || next.isWhitespace()) boundary = index + 1
    }
    return boundary
}

internal fun chunkSentences(text: String): List<String> {
    val pieces = ArrayList<String>()
    val builder = StringBuilder()
    for (character in text) {
        builder.append(character)
        val sentence = builder.toString().trim()
        val ended = character == '.' || character == '!' || character == '?' || character == '\n'
        if (ended && sentence.isNotEmpty() && (builder.length >= MAX_SPOKEN_CHUNK || ended)) {
            if (sentence.length <= MAX_SPOKEN_CHUNK) {
                pieces.add(sentence)
                builder.clear()
            }
        }
        if (builder.length >= MAX_SPOKEN_CHUNK) {
            val splitAt = builder.lastIndexOf(' ').takeIf { it > 20 } ?: builder.length
            val head = builder.substring(0, splitAt).trim()
            if (head.isNotEmpty()) pieces.add(head)
            val rest = builder.substring(splitAt).trim()
            builder.clear()
            builder.append(rest)
        }
    }
    val tail = builder.toString().trim()
    if (tail.isNotEmpty()) pieces.add(tail)
    return pieces
}

internal const val MAX_SPOKEN_CHUNK = 240
