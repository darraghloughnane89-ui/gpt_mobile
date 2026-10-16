package dev.chungjungsoo.gptmobile.data.obsidian

import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeParseException

object VaultMarkdown {
    private val messageLine = Regex("""^<!-- robin:message ([^>]*) -->$""")
    private val attribute = Regex("""([A-Za-z0-9_-]+)=(\S+)""")
    private val linksOpen = "<!-- robin:links -->"
    private val linksClose = "<!-- /robin:links -->"

    fun render(document: VaultDocument): String {
        val lines = mutableListOf("---")
        lines += "id: ${yamlScalar(document.id)}"
        lines += "type: ${document.type.wire}"
        lines += "updated: ${formatEpoch(document.updatedEpochSeconds)}"
        lines.addAll(yamlList("tags", document.tags))
        lines.addAll(yamlList("links", document.links))
        if (!document.name.isNullOrBlank()) lines += "name: ${yamlScalar(document.name)}"
        if (!document.description.isNullOrBlank()) lines += "description: ${yamlScalar(document.description)}"
        if (document.title.isNotBlank()) lines += "title: ${yamlScalar(document.title)}"
        if (!document.project.isNullOrBlank()) lines += "project: ${yamlScalar(document.project)}"
        document.extra.toSortedMap().forEach { (key, value) ->
            if (key.isNotBlank()) lines += "${yamlKey(key)}: ${yamlScalar(value)}"
        }
        lines += "---"
        lines += ""
        if (document.type != VaultNoteType.SKILL && document.title.isNotBlank()) {
            lines += "# ${document.title}"
            lines += ""
        }
        if (document.links.isNotEmpty()) {
            lines += linksOpen
            document.links.forEach { lines += it }
            lines += linksClose
            lines += ""
        }
        if (document.type == VaultNoteType.CHAT) {
            document.messages.forEach { message ->
                lines += messageMarker(message)
                lines += message.content.trimEnd()
                lines += ""
            }
        } else {
            lines += document.body.trimEnd()
            if (document.body.isNotBlank()) lines += ""
        }
        return lines.joinToString("\n").trimEnd() + "\n"
    }

    fun parse(markdown: String): VaultDocument? {
        val normalized = markdown.removePrefix("\uFEFF").replace("\r\n", "\n")
        if (!normalized.startsWith("---\n")) return null
        val end = normalized.indexOf("\n---\n", startIndex = 4)
        if (end < 0) return null
        val frontmatter = normalized.substring(4, end)
        val rawBody = normalized.substring(end + "\n---\n".length)
        val fields = parseFrontmatter(frontmatter)
        val id = fields.scalars["id"]?.takeIf { it.isNotBlank() } ?: return null
        val type = VaultNoteType.fromWire(fields.scalars["type"]) ?: return null
        val updated = parseEpoch(fields.scalars["updated"]) ?: 0L
        val title = fields.scalars["title"].orEmpty()
        val stripped = stripGeneratedChrome(rawBody, title, type)
        val messages = if (type == VaultNoteType.CHAT) parseMessages(stripped) else emptyList()
        val body = if (type == VaultNoteType.CHAT) "" else stripped.trim()
        return VaultDocument(
            id = id,
            type = type,
            updatedEpochSeconds = updated,
            tags = fields.lists["tags"].orEmpty(),
            links = fields.lists["links"].orEmpty(),
            title = title,
            project = fields.scalars["project"],
            name = fields.scalars["name"],
            description = fields.scalars["description"],
            messages = messages,
            body = body,
            extra = fields.scalars.filterKeys { it !in KNOWN_SCALARS }
        )
    }

    fun contentHash(markdown: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(markdown.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun track(document: VaultDocument, path: String): TrackedNote {
        val rendered = render(document)
        return TrackedNote(path = path, hash = contentHash(rendered), document = document)
    }

    fun formatEpoch(epochSeconds: Long): String = Instant.ofEpochSecond(epochSeconds).toString()

    private fun parseEpoch(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value.trim()).epochSecond
        } catch (_: DateTimeParseException) {
            value.trim().toLongOrNull()
        }
    }

    private fun messageMarker(message: VaultMessage): String {
        val platform = message.platform?.takeIf { it.isNotBlank() }?.let { " platform=$it" }.orEmpty()
        return "<!-- robin:message id=${message.id} role=${message.role} at=${formatEpoch(message.atEpochSeconds)}$platform -->"
    }

    private fun parseMessages(body: String): List<VaultMessage> {
        val lines = body.lines()
        val messages = mutableListOf<VaultMessage>()
        var index = 0
        while (index < lines.size) {
            val match = messageLine.matchEntire(lines[index].trim())
            if (match == null) {
                index += 1
                continue
            }
            val attrs = attribute.findAll(match.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            val content = StringBuilder()
            index += 1
            while (index < lines.size && messageLine.matchEntire(lines[index].trim()) == null) {
                if (content.isNotEmpty()) content.append('\n')
                content.append(lines[index])
                index += 1
            }
            val id = attrs["id"] ?: continue
            messages += VaultMessage(
                id = id,
                role = attrs["role"] ?: "user",
                content = content.toString().trimEnd(),
                atEpochSeconds = parseEpoch(attrs["at"]) ?: 0L,
                platform = attrs["platform"]
            )
        }
        return messages
    }

    private fun stripGeneratedChrome(rawBody: String, title: String, type: VaultNoteType): String {
        var text = rawBody.trim('\n')
        if (type != VaultNoteType.SKILL && title.isNotBlank()) {
            val heading = "# $title"
            if (text.lineSequence().firstOrNull()?.trim() == heading) {
                text = text.lineSequence().drop(1).joinToString("\n").trim('\n')
            }
        }
        val open = text.indexOf(linksOpen)
        val close = text.indexOf(linksClose)
        if (open >= 0 && close > open) {
            text = (text.substring(0, open) + text.substring(close + linksClose.length)).trim('\n')
        }
        return text
    }

    private fun yamlList(key: String, values: List<String>): List<String> {
        if (values.isEmpty()) return listOf("$key: []")
        return listOf("$key:") + values.map { "  - ${yamlScalar(it)}" }
    }

    private fun yamlScalar(value: String): String {
        val needsQuotes = value.isEmpty() ||
            value.any { it == ':' || it == '#' || it == '"' || it == '\n' || it == '[' || it == ']' || it == '{' || it == '}' } ||
            value.trim() != value ||
            value.startsWith("*") ||
            value.startsWith("&") ||
            value.startsWith("!") ||
            value.startsWith("'") ||
            value.equals("true", true) ||
            value.equals("false", true) ||
            value.equals("null", true)
        if (!needsQuotes) return value
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return "\"$escaped\""
    }

    private fun yamlKey(key: String): String = key.filter { it.isLetterOrDigit() || it == '_' || it == '-' }

    private fun parseFrontmatter(source: String): Frontmatter {
        val scalars = linkedMapOf<String, String>()
        val lists = linkedMapOf<String, List<String>>()
        var pendingList: String? = null
        val items = mutableListOf<String>()
        fun finishList() {
            val key = pendingList ?: return
            lists[key] = items.toList()
            items.clear()
            pendingList = null
        }
        source.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            if (pendingList != null && line.startsWith("  - ")) {
                items += unquote(line.removePrefix("  - ").trim())
                return@forEach
            }
            finishList()
            val separator = line.indexOf(':')
            if (separator <= 0) return@forEach
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            if (value.isEmpty()) {
                pendingList = key
            } else if (value == "[]") {
                lists[key] = emptyList()
            } else {
                scalars[key] = unquote(value)
            }
        }
        finishList()
        return Frontmatter(scalars, lists)
    }

    private fun unquote(value: String): String {
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length - 1)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        if (value.length >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length - 1).replace("''", "'")
        }
        return value
    }

    private data class Frontmatter(
        val scalars: Map<String, String>,
        val lists: Map<String, List<String>>
    )

    private val KNOWN_SCALARS = setOf("id", "type", "updated", "title", "project", "name", "description")
}
