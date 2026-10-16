package dev.chungjungsoo.gptmobile.data.project.archive

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object ProjectArchiveZip {
    const val MAX_ENTRY_BYTES = 32 * 1024 * 1024
    const val MAX_ENTRIES = 2_000
    const val MAX_TOTAL_BYTES = 128 * 1024 * 1024

    private val json = Json { prettyPrint = true }

    fun read(input: InputStream): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    if (entries.size >= MAX_ENTRIES) throw ProjectArchiveException(ProjectArchiveFailure.TOO_LARGE)
                    if (isSafePath(entry.name)) {
                        val bytes = readCapped(zip, MAX_ENTRY_BYTES)
                            ?: throw ProjectArchiveException(ProjectArchiveFailure.TOO_LARGE)
                        total += bytes.size
                        if (total > MAX_TOTAL_BYTES) throw ProjectArchiveException(ProjectArchiveFailure.TOO_LARGE)
                        entries += ArchiveEntry(entry.name, bytes)
                    }
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    fun write(project: ImportedProject): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            put(zip, "manifest.json", manifest(project).toByteArray(Charsets.UTF_8))
            put(zip, "instructions.md", project.instructions.toByteArray(Charsets.UTF_8))
            project.files.forEach { file ->
                val path = if (file.relativePath.startsWith("files/")) file.relativePath else "files/${file.displayName}"
                put(zip, path, file.bytes)
            }
            project.chats.forEach { chat ->
                put(zip, "chats/${safeFileName(chat.externalId)}.json", chatDocument(chat).toByteArray(Charsets.UTF_8))
            }
        }
        return output.toByteArray()
    }

    fun readCapped(input: InputStream, maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun put(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun manifest(project: ImportedProject): String {
        val element = buildJsonObject {
            put("format", "robin-project")
            put("version", 1)
            put("name", project.name)
            put("icon", project.icon)
            put("color", project.color.toLong() and 0xffffffffL)
            put("importKey", project.importKey)
        }
        return encode(element)
    }

    private fun chatDocument(chat: ImportedChat): String {
        val element = buildJsonObject {
            put("id", chat.externalId)
            put("title", chat.title)
            put("createdAt", chat.createdAt)
            put("updatedAt", chat.updatedAt)
            put(
                "messages",
                buildJsonArray {
                    chat.messages.forEach { message ->
                        add(
                            buildJsonObject {
                                put("role", if (message.role == ImportedRole.USER) "user" else "assistant")
                                put("text", message.text)
                                put("createdAt", message.createdAt)
                            }
                        )
                    }
                }
            )
        }
        return encode(element)
    }

    private fun encode(element: JsonElement): String = json.encodeToString(JsonElement.serializer(), element)

    private fun isSafePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.contains(":")) return false
        return normalized.split('/').none { it == ".." }
    }

    private fun safeFileName(id: String): String {
        val cleaned = id.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').take(80)
        return cleaned.ifBlank { "chat" }
    }
}
