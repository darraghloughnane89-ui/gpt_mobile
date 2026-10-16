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
    /** One stored file. Images can be larger on disk only after the caller opts to keep the original, and still only up to this cap. */
    const val MAX_ENTRY_BYTES = 32 * 1024 * 1024

    /** Chat JSON and markdown that have to be parsed as a document. */
    const val MAX_CATALOG_BYTES = 256 * 1024 * 1024

    /** Uncompressed bytes for the whole import, including files expanded out of nested zips. */
    const val MAX_TOTAL_BYTES = 1024L * 1024L * 1024L
    const val MAX_ENTRIES = 5_000
    const val MAX_IMAGE_EDGE = 2048
    const val MAX_TABLE_ROWS = 200
    const val MAX_TABLE_COLS = 20
    const val MAX_TABLE_SHEETS = 8
    const val MAX_CELL_CHARS = 400
    const val MAX_ZIP_DEPTH = 4

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
