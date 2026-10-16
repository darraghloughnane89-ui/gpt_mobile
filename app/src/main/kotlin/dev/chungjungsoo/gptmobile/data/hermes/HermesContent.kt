package dev.chungjungsoo.gptmobile.data.hermes

import java.io.File
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class HermesTranscriptTurn(
    val speaker: String,
    val text: String,
    val attachmentNames: List<String> = emptyList()
)

object HermesTranscript {
    fun full(title: String, turns: List<HermesTranscriptTurn>): String = render(
        title = title,
        turns = turns,
        limit = HermesDefaults.MAX_TRANSCRIPT_CHARS,
        summary = false
    )

    fun summary(title: String, turns: List<HermesTranscriptTurn>): String {
        val users = turns.filter { it.speaker.equals("You", ignoreCase = true) || it.speaker.equals("User", ignoreCase = true) }
        val assistants = turns.filterNot { users.contains(it) }
        val kept = listOfNotNull(users.firstOrNull(), users.lastOrNull(), assistants.lastOrNull()).distinct()
        val body = render(title = title, turns = kept, limit = HermesDefaults.MAX_SUMMARY_CHARS, summary = true)
        val count = "Turns: ${turns.size}. Attachments: ${turns.flatMap { it.attachmentNames }.distinct().joinToString().ifBlank { "none" }}."
        return "$body\n\n$count".take(HermesDefaults.MAX_SUMMARY_CHARS)
    }

    private fun render(
        title: String,
        turns: List<HermesTranscriptTurn>,
        limit: Int,
        summary: Boolean
    ): String {
        val heading = if (summary) "Summary of \"$title\"" else "Transcript of \"$title\""
        val body = buildString {
            appendLine(heading)
            appendLine()
            turns.forEach { turn ->
                append(turn.speaker)
                append(": ")
                append(turn.text.trim())
                if (turn.attachmentNames.isNotEmpty()) {
                    append(" [files: ")
                    append(turn.attachmentNames.joinToString())
                    append(']')
                }
                appendLine()
                appendLine()
            }
        }.trim()
        return if (body.length <= limit) body else body.take(limit) + "\n…[truncated]"
    }
}

data class HermesPreparedFile(
    val displayName: String,
    val mimeType: String,
    val bytes: ByteArray?,
    val sizeBytes: Long,
    val missing: Boolean
)

object HermesFileLoader {
    fun load(attachment: HermesAttachment): HermesPreparedFile {
        val name = attachment.displayName.ifBlank { File(attachment.path).name.ifBlank { "attachment" } }
        if (attachment.path.isBlank()) {
            return HermesPreparedFile(name, attachment.mimeType, null, 0L, true)
        }
        val file = File(attachment.path)
        if (!file.isFile) {
            return HermesPreparedFile(name, attachment.mimeType, null, 0L, true)
        }
        val mime = attachment.mimeType.ifBlank { guessMime(file.name) }
        val size = file.length()
        val bytes = if (size in 1..HermesDefaults.MAX_READ_BYTES) file.readBytes() else null
        return HermesPreparedFile(name, mime, bytes, size, false)
    }

    fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "txt", "md", "csv", "json", "xml", "kt", "kts", "log" -> "text/plain"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}

data class HermesUserContent(
    val text: String,
    val imageDataUrls: List<String> = emptyList()
)

fun buildHermesUserContent(
    kind: HermesHandoffKind,
    text: String,
    files: List<HermesPreparedFile>
): HermesUserContent {
    val images = mutableListOf<String>()
    val notes = StringBuilder()
    files.forEach { file ->
        when {
            file.missing -> notes.appendLine("Missing file: ${file.displayName}.")
            file.mimeType.startsWith("image/") && file.bytes != null && file.bytes.size <= HermesDefaults.MAX_IMAGE_BYTES -> {
                val encoded = Base64.getEncoder().encodeToString(file.bytes)
                images += "data:${file.mimeType};base64,$encoded"
            }
            isTextFile(file) && file.bytes != null -> {
                val excerpt = file.bytes.decodeToString().take(HermesDefaults.MAX_TEXT_FILE_CHARS)
                notes.appendLine("File ${file.displayName} (${file.mimeType}, ${file.sizeBytes} bytes):")
                notes.appendLine(excerpt)
            }
            else -> notes.appendLine(
                "File ${file.displayName} (${file.mimeType.ifBlank { "unknown type" }}, ${file.sizeBytes} bytes) " +
                    "was not inlined. The Hermes API accepts images inline and does not accept other uploads."
            )
        }
    }
    val heading = when (kind) {
        HermesHandoffKind.MESSAGE -> "Message handed off from the phone."
        HermesHandoffKind.TRANSCRIPT -> "Full chat transcript handed off from the phone."
        HermesHandoffKind.SUMMARY -> "Short summary of a chat handed off from the phone."
        HermesHandoffKind.FILE -> "File handed off from the phone."
        HermesHandoffKind.TASK -> "Task from the phone."
    }
    val body = buildString {
        appendLine(heading)
        appendLine()
        append(text.trim().ifBlank { "Please look at the attached material." })
        if (notes.isNotEmpty()) {
            appendLine()
            appendLine()
            append(notes.toString().trim())
        }
    }
    return HermesUserContent(text = body, imageDataUrls = images)
}

fun chatCompletionBody(
    model: String,
    history: List<Pair<String, String>>,
    user: HermesUserContent
): String {
    val messages = buildJsonArray {
        add(message("system", JsonPrimitive(SYSTEM_PREFACE)))
        var used = 0
        history.forEach { (role, text) ->
            if (used >= HermesDefaults.MAX_HISTORY_CHARS) return@forEach
            val clipped = text.take(HermesDefaults.MAX_HISTORY_CHARS - used)
            used += clipped.length
            add(message(role, JsonPrimitive(clipped)))
        }
        add(message("user", userContent(user)))
    }
    val payload = buildJsonObject {
        put("model", model)
        put("stream", true)
        put("messages", messages)
    }
    return Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload)
}

private fun userContent(user: HermesUserContent): JsonElement {
    if (user.imageDataUrls.isEmpty()) return JsonPrimitive(user.text)
    return buildJsonArray {
        add(
            buildJsonObject {
                put("type", "text")
                put("text", user.text)
            }
        )
        user.imageDataUrls.forEach { url ->
            add(
                buildJsonObject {
                    put("type", "image_url")
                    put(
                        "image_url",
                        buildJsonObject {
                            put("url", url)
                        }
                    )
                }
            )
        }
    }
}

private fun message(role: String, content: JsonElement) = buildJsonObject {
    put("role", role)
    put("content", content)
}

private fun isTextFile(file: HermesPreparedFile): Boolean {
    if (file.mimeType.startsWith("text/")) return true
    val ext = file.displayName.substringAfterLast('.', "").lowercase()
    return ext in TEXT_EXTENSIONS
}

private val TEXT_EXTENSIONS = setOf("txt", "md", "csv", "json", "xml", "kt", "kts", "log", "yaml", "yml")

private const val SYSTEM_PREFACE =
    "You are receiving a handoff from a phone. The user text is the task. Keep your tools, memory, and skills."
