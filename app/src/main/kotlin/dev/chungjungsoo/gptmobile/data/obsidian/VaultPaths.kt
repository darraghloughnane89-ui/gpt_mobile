package dev.chungjungsoo.gptmobile.data.obsidian

import dev.chungjungsoo.gptmobile.util.Brand
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object VaultPaths {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun chat(
        root: String,
        project: String,
        createdEpochSeconds: Long,
        title: String,
        id: String
    ): String {
        val day = Instant.ofEpochSecond(createdEpochSeconds)
            .atOffset(ZoneOffset.UTC)
            .format(dateFormatter)
        val safeTitle = sanitize(title).ifBlank { sanitize(id) }.ifBlank { "chat" }
        val safeProject = sanitize(project).ifBlank { "Inbox" }
        return "${normalizeRoot(root)}/Chats/$safeProject/$day $safeTitle.md"
    }

    fun memory(root: String, title: String, id: String): String {
        val name = sanitize(title).ifBlank { sanitize(id) }.ifBlank { "memory" }
        return "${normalizeRoot(root)}/Memory/$name.md"
    }

    fun skill(root: String, name: String, id: String): String {
        val folder = sanitize(name).ifBlank { sanitize(id) }.ifBlank { "skill" }
        return "${normalizeRoot(root)}/Skills/$folder/SKILL.md"
    }

    fun bot(root: String, name: String, id: String): String {
        val file = sanitize(name).ifBlank { sanitize(id) }.ifBlank { "bot" }
        return "${normalizeRoot(root)}/Bots/$file.md"
    }

    fun project(root: String, name: String, id: String): String {
        val file = sanitize(name).ifBlank { sanitize(id) }.ifBlank { "project" }
        return "${normalizeRoot(root)}/Projects/$file.md"
    }

    fun wikiLink(root: String, relativeNote: String, alias: String? = null): String {
        val target = "${normalizeRoot(root)}/$relativeNote".trim('/')
        return if (alias.isNullOrBlank() || alias == target) "[[$target]]" else "[[$target|$alias]]"
    }

    fun projectWikiLink(root: String, projectName: String): String {
        val file = sanitize(projectName).ifBlank { "Inbox" }
        return wikiLink(root, "Projects/$file", file)
    }

    fun confine(path: String, root: String): String? {
        val normalizedRoot = normalizeRoot(root)
        val raw = path.trim().replace('\\', '/')
        if (raw.isEmpty() || raw.startsWith("/") || raw.contains(':')) return null
        val segments = raw.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        val normalized = segments.joinToString("/")
        if (normalized != normalizedRoot && !normalized.startsWith("$normalizedRoot/")) return null
        return normalized
    }

    fun isUnderRoot(path: String, root: String): Boolean = confine(path, root) != null

    fun normalizeRoot(root: String): String {
        val trimmed = root.trim().trim('/').replace('\\', '/')
        val collapsed = trimmed.split('/').filter { it.isNotBlank() && it != "." && it != ".." }.joinToString("/")
        return collapsed.ifBlank { Brand.VAULT_ROOT }
    }

    fun sanitize(value: String): String {
        val cleaned = value
            .replace(Regex("""[\\/:*?"<>|#\[\]]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trim('.')
        return cleaned.take(80)
    }
}
