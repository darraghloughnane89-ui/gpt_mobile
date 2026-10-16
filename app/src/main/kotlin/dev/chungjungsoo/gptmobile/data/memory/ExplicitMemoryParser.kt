package dev.chungjungsoo.gptmobile.data.memory

object ExplicitMemoryParser {
    private val patterns = listOf(
        Regex("(?is)^\\s*(?:please\\s+)?remember\\s+that\\s+(.+?)\\s*$"),
        Regex("(?is)^\\s*(?:please\\s+)?remember\\s*:\\s*(.+?)\\s*$")
    )

    fun extract(message: String): String? {
        val match = patterns.firstNotNullOfOrNull { pattern -> pattern.find(message) } ?: return null
        return match.groupValues[1].replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() }
    }
}
