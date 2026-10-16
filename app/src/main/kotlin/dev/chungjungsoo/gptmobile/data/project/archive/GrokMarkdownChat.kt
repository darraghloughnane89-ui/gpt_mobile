package dev.chungjungsoo.gptmobile.data.project.archive

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Grok's markdown export: a metadata header, then `## User` / `## Grok` sections split by `---`.
 */
object GrokMarkdownChat {
    const val EMPTY_PLACEHOLDER = "(empty)"

    private val userHeading = Regex("""^##\s+user\s*$""", RegexOption.IGNORE_CASE)
    private val grokHeading = Regex("""^##\s+grok\s*$""", RegexOption.IGNORE_CASE)
    private val metaLine = Regex(
        """^\*{0,2}\s*(date|chat id|url|messages)\s*\*{0,2}\s*:\s*\*{0,2}\s*(.*?)\s*\*{0,2}\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val duration = """\d+(?:\.\d+)?(?:\s*(?:s|sec|secs|seconds|m|min|mins|minutes|h|hr|hrs|hours))?"""
    private val statusLine = Regex(
        """^(?:worked|thought)\s+for\s+$duration(?:\s+$duration)*\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val longDates = listOf(
        DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US),
        DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US),
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.US),
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.US)
    )

    fun isGrokMarkdown(text: String): Boolean = text.lineSequence().any { grokHeading.matches(it.trim()) }

    fun parse(fallbackId: String, text: String): ImportedChat {
        var title = fallbackId
        var chatId = fallbackId
        var createdAt = 0L
        var role: ImportedRole? = null
        var inSection = false
        val body = StringBuilder()
        val messages = mutableListOf<ImportedMessage>()

        fun flush() {
            if (!inSection || role == null) {
                body.clear()
                return
            }
            val cleaned = stripLeadingStatus(body.toString()).trim()
            messages += ImportedMessage(
                role = role ?: ImportedRole.USER,
                text = cleaned.ifEmpty { EMPTY_PLACEHOLDER },
                createdAt = createdAt
            )
            body.clear()
            inSection = false
        }

        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                !inSection && trimmed.startsWith("# ") && !trimmed.startsWith("## ") && title == fallbackId -> {
                    title = trimmed.removePrefix("# ").trim().ifBlank { fallbackId }
                }
                userHeading.matches(trimmed) -> {
                    flush()
                    role = ImportedRole.USER
                    inSection = true
                }
                grokHeading.matches(trimmed) -> {
                    flush()
                    role = ImportedRole.ASSISTANT
                    inSection = true
                }
                trimmed == "---" -> if (inSection) flush()
                inSection -> {
                    if (body.isNotEmpty()) body.append('\n')
                    body.append(line)
                }
                else -> readHeader(trimmed, onId = { chatId = it }, onDate = { createdAt = it })
            }
        }
        flush()
        val updated = messages.maxOfOrNull { it.createdAt }?.takeIf { it > 0 } ?: createdAt
        return ImportedChat(
            externalId = chatId.ifBlank { fallbackId },
            title = title.ifBlank { fallbackId },
            createdAt = createdAt,
            updatedAt = updated,
            messages = messages
        )
    }

    private fun readHeader(line: String, onId: (String) -> Unit, onDate: (Long) -> Unit) {
        val match = metaLine.matchEntire(line) ?: return
        val key = match.groupValues[1].lowercase(Locale.US)
        val value = match.groupValues[2].trim()
        if (value.isEmpty()) return
        when (key) {
            "chat id" -> onId(value)
            "date" -> parseDate(value)?.let(onDate)
            else -> Unit
        }
    }

    private fun stripLeadingStatus(text: String): String {
        val lines = text.lines()
        var index = 0
        while (index < lines.size) {
            val trimmed = lines[index].trim()
            if (trimmed.isEmpty() || statusLine.matches(trimmed)) {
                index++
            } else {
                break
            }
        }
        return lines.drop(index).joinToString("\n")
    }

    private fun parseDate(value: String): Long? {
        try {
            return Instant.parse(value).epochSecond
        } catch (_: Exception) {
            // try the next pattern
        }
        try {
            return OffsetDateTime.parse(value).toEpochSecond()
        } catch (_: Exception) {
            // try the next pattern
        }
        try {
            return LocalDateTime.parse(value).toEpochSecond(ZoneOffset.UTC)
        } catch (_: Exception) {
            // try the next pattern
        }
        try {
            return LocalDate.parse(value).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        } catch (_: Exception) {
            // try explicit patterns
        }
        longDates.forEach { formatter ->
            try {
                return LocalDateTime.parse(value, formatter).toEpochSecond(ZoneOffset.UTC)
            } catch (_: Exception) {
                // try date-only
            }
            try {
                return LocalDate.parse(value, formatter).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
            } catch (_: Exception) {
                // next formatter
            }
        }
        return null
    }
}
