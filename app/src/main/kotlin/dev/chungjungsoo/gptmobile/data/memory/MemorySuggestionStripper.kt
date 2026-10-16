package dev.chungjungsoo.gptmobile.data.memory

data class MemorySuggestionTail(
    val visible: String,
    val suggestion: String?
)

/**
 * Removes a trailing memory suggestion from streamed text.
 * Ordinary text is emitted immediately. Only a suffix that is a prefix of the marker is held back.
 */
class MemorySuggestionStripper(
    private val marker: String = MemoryPromptComposer.MARKER
) {
    private val pending = StringBuilder()

    fun accept(chunk: String): String {
        if (chunk.isEmpty()) return ""
        pending.append(chunk)
        val text = pending.toString()
        val markerIndex = text.indexOf(marker)
        if (markerIndex >= 0) {
            val visible = text.substring(0, markerIndex).trimEnd()
            pending.clear()
            pending.append(text.substring(markerIndex))
            return visible
        }
        val hold = markerPrefixLength(text)
        if (hold == 0) {
            pending.clear()
            return text
        }
        val visible = text.dropLast(hold)
        pending.clear()
        pending.append(text.takeLast(hold))
        return visible
    }

    fun finish(): MemorySuggestionTail {
        val text = pending.toString()
        pending.clear()
        val markerIndex = text.indexOf(marker)
        if (markerIndex < 0) {
            return MemorySuggestionTail(text, null)
        }
        val suggestion = text.substring(markerIndex + marker.length)
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val visible = text.substring(0, markerIndex).trimEnd()
        return MemorySuggestionTail(visible, suggestion)
    }

    private fun markerPrefixLength(text: String): Int {
        val max = minOf(text.length, marker.length - 1)
        for (length in max downTo 1) {
            if (marker.startsWith(text.takeLast(length))) return length
        }
        return 0
    }
}
