package dev.chungjungsoo.gptmobile.data.voice

class SpokenSentenceQueue {
    private val pending = StringBuilder()
    private var sourceLength = 0
    private var sourceSnapshot = ""

    fun reset() {
        pending.clear()
        sourceLength = 0
        sourceSnapshot = ""
    }

    fun push(fullText: String, flush: Boolean): List<String> {
        if (sourceSnapshot.isNotEmpty() && !fullText.startsWith(sourceSnapshot)) {
            reset()
        }
        if (fullText.length >= sourceLength && (sourceSnapshot.isEmpty() || fullText.startsWith(sourceSnapshot))) {
            pending.append(fullText.substring(sourceLength))
            sourceLength = fullText.length
            sourceSnapshot = fullText
        }
        return drain(flush)
    }

    private fun drain(flush: Boolean): List<String> {
        val (sentences, rest) = splitSpeakableChunks(pending.toString(), flush)
        pending.clear()
        pending.append(rest)
        return sentences
    }
}

internal fun splitSpeakableChunks(text: String, flush: Boolean): Pair<List<String>, String> {
    val sentences = mutableListOf<String>()
    var start = 0
    var index = 0
    while (index < text.length) {
        val boundary = sentenceBoundaryEnd(text, index)
        if (boundary != null) {
            val sentence = text.substring(start, boundary).trim()
            if (sentence.isNotEmpty()) sentences += sentence
            start = boundary
            while (start < text.length && text[start].isWhitespace()) start++
            index = start
        } else {
            index++
        }
    }
    var rest = text.substring(start)
    if (!flush && rest.length >= MAX_SPOKEN_CHUNK) {
        val splitAt = rest.lastIndexOf(' ', MAX_SPOKEN_CHUNK).takeIf { it > 40 } ?: MAX_SPOKEN_CHUNK
        val forced = rest.substring(0, splitAt).trim()
        if (forced.isNotEmpty()) sentences += forced
        rest = rest.substring(splitAt).trimStart()
    }
    if (flush) {
        val tail = rest.trim()
        if (tail.isNotEmpty()) sentences += tail
        rest = ""
    }
    return sentences to rest
}

internal fun sentenceBoundaryEnd(text: String, index: Int): Int? {
    val character = text[index]
    if (character == '\n') return index + 1
    if (character != '.' && character != '!' && character != '?') return null
    val previous = text.getOrNull(index - 1)
    val next = text.getOrNull(index + 1)
    if (character == '.' && previous?.isDigit() == true && next?.isDigit() == true) return null
    if (character == '.' && isSpokenAbbreviation(text, index)) return null
    if (next == null || next.isWhitespace()) return index + 1
    return null
}

private fun isSpokenAbbreviation(text: String, periodIndex: Int): Boolean {
    var start = periodIndex - 1
    while (start >= 0 && text[start].isLetter()) start--
    val word = text.substring(start + 1, periodIndex).lowercase()
    return word in SPOKEN_ABBREVIATIONS
}

private val SPOKEN_ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs")
