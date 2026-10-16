package dev.chungjungsoo.gptmobile.data.widget

object RoutineSummary {
    const val MAX_CHARS = 180
    const val MORNING_BRIEFING_ID = "morning-briefing"

    fun fromAssistantTexts(texts: List<String>, maxChars: Int = MAX_CHARS): String? {
        val raw = texts.asReversed().firstOrNull { it.isNotBlank() } ?: return null
        val cleaned = WidgetSecrets.redact(raw).replace(WHITESPACE, " ").trim()
        if (cleaned.isBlank() || cleaned == WidgetSecrets.REDACTED) return null
        if (cleaned.length <= maxChars) return cleaned
        return cleaned.take(maxChars - 1).trimEnd() + "…"
    }

    fun routineIdFor(title: String, chatId: Int): String =
        if (title.contains("brief", ignoreCase = true)) MORNING_BRIEFING_ID else "chat-$chatId"

    private val WHITESPACE = Regex("\\s+")
}
