package dev.chungjungsoo.gptmobile.data.project.archive

import dev.chungjungsoo.gptmobile.data.project.ProjectColors
import dev.chungjungsoo.gptmobile.data.project.ProjectIcons
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Turns a zip or folder listing into Robin projects.
 * Detection order is a Robin manifest, ChatGPT `conversations.json`, Claude, Grok, then a Robin folder.
 */
object ProjectArchiveParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(entries: List<ArchiveEntry>): List<ImportedProject> {
        val normalized = normalize(entries)
        if (normalized.isEmpty()) throw ProjectArchiveException(ProjectArchiveFailure.EMPTY)
        return when {
            hasRobinManifest(normalized) -> listOf(parseRobin(normalized))
            isChatGpt(normalized) -> listOf(parseChatGpt(normalized))
            isClaude(normalized) -> parseClaude(normalized)
            isGrok(normalized) -> listOf(parseGrok(normalized))
            isRobinLayout(normalized) -> listOf(parseRobin(normalized))
            else -> throw ProjectArchiveException(ProjectArchiveFailure.UNRECOGNIZED)
        }
    }

    fun normalize(entries: List<ArchiveEntry>): List<ArchiveEntry> {
        val cleaned = entries.mapNotNull { entry ->
            val path = entry.path.replace('\\', '/').trim().trimStart('/')
            if (path.isEmpty() || path.endsWith("/")) return@mapNotNull null
            val parts = path.split('/')
            if (parts.any { it.isEmpty() || it == "." || it == ".." }) return@mapNotNull null
            val name = parts.last()
            if (name == ".DS_Store" || parts.first() == "__MACOSX") return@mapNotNull null
            ArchiveEntry(path, entry.bytes)
        }
        val roots = cleaned.map { it.path.substringBefore('/') }.toSet()
        val nested = cleaned.isNotEmpty() && cleaned.all { it.path.contains('/') } && roots.size == 1
        if (!nested) return cleaned
        val prefix = roots.single() + "/"
        return cleaned.map { ArchiveEntry(it.path.removePrefix(prefix), it.bytes) }
    }

    private fun hasRobinManifest(entries: List<ArchiveEntry>): Boolean {
        val manifest = jsonAt(entries, "manifest.json") as? JsonObject ?: return false
        return manifest["format"]?.text() == "robin-project"
    }

    private fun isRobinLayout(entries: List<ArchiveEntry>): Boolean = entries.any { entry ->
        entry.path.equals("instructions.md", ignoreCase = true) ||
            entry.path.startsWith("files/") ||
            entry.path.startsWith("chats/")
    }

    private fun isChatGpt(entries: List<ArchiveEntry>): Boolean {
        val root = jsonAt(entries, "conversations.json") as? JsonArray ?: return false
        val first = root.firstOrNull() as? JsonObject ?: return false
        return first["mapping"] is JsonObject
    }

    private fun isClaude(entries: List<ArchiveEntry>): Boolean {
        if (claudeProjects(jsonAt(entries, "projects.json")).isNotEmpty()) return true
        val conversations = jsonAt(entries, "conversations.json") ?: return false
        return conversationArray(conversations).any { it["chat_messages"] is JsonArray }
    }

    private fun isGrok(entries: List<ArchiveEntry>): Boolean = grokPayload(entries) != null

    private fun parseRobin(entries: List<ArchiveEntry>): ImportedProject {
        val manifest = jsonAt(entries, "manifest.json") as? JsonObject
        val instructions = textAt(entries, "instructions.md")
        val files = entries.filter { entry ->
            entry.path.startsWith("files/") && entry.bytes.isNotEmpty() && entry.path.substringAfterLast('/') != "import-map.json"
        }.map { entry ->
            ImportedFile(
                relativePath = entry.path,
                displayName = entry.path.substringAfterLast('/'),
                bytes = entry.bytes
            )
        }
        val chats = entries.filter { entry ->
            entry.path.startsWith("chats/") && (entry.path.endsWith(".json") || entry.path.endsWith(".md"))
        }.mapNotNull { entry -> parseRobinChat(entry) }
        val name = manifest?.get("name")?.text()?.trim()?.ifBlank { null } ?: "Imported project"
        val icon = manifest?.get("icon")?.text()?.takeIf { it in ProjectIcons.all } ?: ProjectIcons.FOLDER
        val color = colorOf(manifest?.get("color"))
        val key = manifest?.get("importKey")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: derivedKey(name, chats.map { it.externalId }, files.map { it.relativePath })
        return ImportedProject(
            importKey = key,
            name = name,
            instructions = instructions,
            icon = icon,
            color = color,
            source = ImportSource.ROBIN,
            files = files,
            chats = chats
        )
    }

    private fun parseRobinChat(entry: ArchiveEntry): ImportedChat? {
        val fileName = entry.path.substringAfterLast('/').substringBeforeLast('.')
        if (entry.path.endsWith(".md")) return parseMarkdownChat(fileName, entry.bytes.toString(Charsets.UTF_8))
        val obj = parseJson(entry.bytes) as? JsonObject ?: return null
        val id = obj["id"]?.text()?.trim()?.ifBlank { null } ?: fileName
        val created = epochSeconds(obj["createdAt"], 0L)
        val updated = epochSeconds(obj["updatedAt"], created)
        val messages = (obj["messages"] as? JsonArray)?.mapNotNull { element ->
            val message = element as? JsonObject ?: return@mapNotNull null
            val role = when (message["role"]?.text()?.lowercase()) {
                "user", "human" -> ImportedRole.USER
                "assistant" -> ImportedRole.ASSISTANT
                else -> return@mapNotNull null
            }
            val text = message["text"]?.text()?.trim().orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            ImportedMessage(role, text, epochSeconds(message["createdAt"], created))
        }.orEmpty()
        return ImportedChat(
            externalId = id,
            title = obj["title"]?.text()?.trim()?.ifBlank { null } ?: fileName,
            createdAt = created,
            updatedAt = updated,
            messages = messages
        )
    }

    private fun parseMarkdownChat(id: String, text: String): ImportedChat {
        var title = id
        var role: ImportedRole? = null
        val buffer = StringBuilder()
        val messages = mutableListOf<ImportedMessage>()
        fun flush() {
            val body = buffer.toString().trim()
            val current = role
            if (current != null && body.isNotEmpty()) {
                messages += ImportedMessage(current, body, 0L)
            }
            buffer.clear()
        }
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            when {
                title == id && trimmed.startsWith("# ") -> title = trimmed.removePrefix("# ").trim().ifBlank { id }
                trimmed.equals("## user", ignoreCase = true) || trimmed.equals("**user**", ignoreCase = true) -> {
                    flush()
                    role = ImportedRole.USER
                }
                trimmed.equals("## assistant", ignoreCase = true) || trimmed.equals("**assistant**", ignoreCase = true) -> {
                    flush()
                    role = ImportedRole.ASSISTANT
                }
                else -> {
                    if (buffer.isNotEmpty()) buffer.append('\n')
                    buffer.append(line)
                }
            }
        }
        flush()
        return ImportedChat(id, title, 0L, 0L, messages)
    }

    private fun parseChatGpt(entries: List<ArchiveEntry>): ImportedProject {
        val array = jsonAt(entries, "conversations.json") as? JsonArray
            ?: throw ProjectArchiveException(ProjectArchiveFailure.UNRECOGNIZED)
        val meta = jsonAt(entries, "project.json") as? JsonObject
        val parsed = array.mapIndexedNotNull { index, element ->
            (element as? JsonObject)?.let { chatgptConversation(it, index) }
        }
        val systems = parsed.map { it.system }.filter { it.isNotBlank() }
        val hoisted = if (systems.isNotEmpty() && systems.all { it == systems.first() }) systems.first() else ""
        val override = textAt(entries, "instructions.md").trim()
        val sourceId = meta?.get("id")?.text()?.trim()?.ifBlank { null }
        return ImportedProject(
            importKey = if (sourceId == null) "chatgpt" else "chatgpt:$sourceId",
            name = meta?.get("name")?.text()?.trim()?.ifBlank { null } ?: "ChatGPT",
            instructions = override.ifBlank { hoisted },
            source = ImportSource.CHATGPT,
            files = filesUnder(entries),
            chats = parsed.map { it.chat }
        )
    }

    private data class ChatWithSystem(val chat: ImportedChat, val system: String)

    private fun chatgptConversation(obj: JsonObject, index: Int): ChatWithSystem? {
        val mapping = obj["mapping"] as? JsonObject ?: return null
        val leaf = obj["current_node"]?.text()?.takeIf { it in mapping } ?: longestLeaf(mapping)
        val nodes = walk(mapping, leaf)
        val systemParts = mutableListOf<String>()
        val messages = mutableListOf<ImportedMessage>()
        val created = epochSeconds(obj["create_time"], 0L)
        nodes.forEach { node ->
            val message = node["message"] as? JsonObject ?: return@forEach
            val role = message["author"]?.jsonObject?.get("role")?.text()?.lowercase() ?: return@forEach
            val text = chatgptText(message).trim()
            if (text.isEmpty()) return@forEach
            when (role) {
                "system" -> systemParts += text
                "user" -> messages += ImportedMessage(ImportedRole.USER, text, epochSeconds(message["create_time"], created))
                "assistant" -> messages += ImportedMessage(ImportedRole.ASSISTANT, text, epochSeconds(message["create_time"], created))
            }
        }
        val id = obj["id"]?.text()?.trim()?.ifBlank { null }
            ?: obj["conversation_id"]?.text()?.trim()?.ifBlank { null }
            ?: "chatgpt-$index"
        val updated = epochSeconds(obj["update_time"], created)
        return ChatWithSystem(
            chat = ImportedChat(
                externalId = id,
                title = obj["title"]?.text()?.trim()?.ifBlank { null } ?: "Chat",
                createdAt = created,
                updatedAt = updated,
                messages = messages
            ),
            system = systemParts.joinToString("\n\n")
        )
    }

    private fun chatgptText(message: JsonObject): String {
        val content = message["content"]
        val contentObject = content as? JsonObject
        val parts = contentObject?.get("parts") as? JsonArray
        if (parts != null) {
            return parts.mapNotNull { part ->
                when (part) {
                    is JsonPrimitive -> part.text()
                    is JsonObject -> part["text"]?.text()
                    else -> null
                }
            }.filter { it.isNotBlank() }.joinToString("\n")
        }
        return contentObject?.get("text")?.text() ?: content?.text().orEmpty()
    }

    private fun longestLeaf(mapping: JsonObject): String? {
        val leaves = mapping.keys.filter { id ->
            val children = mapping[id]?.jsonObject?.get("children") as? JsonArray
            children == null || children.isEmpty()
        }
        return leaves.maxByOrNull { id -> walk(mapping, id).size }
    }

    private fun walk(mapping: JsonObject, start: String?): List<JsonObject> {
        val chain = mutableListOf<JsonObject>()
        val seen = mutableSetOf<String>()
        var node = start
        while (node != null && seen.add(node)) {
            val obj = mapping[node] as? JsonObject ?: break
            chain += obj
            node = obj["parent"]?.text()
        }
        chain.reverse()
        return chain
    }

    private fun parseClaude(entries: List<ArchiveEntry>): List<ImportedProject> {
        val projectObjects = claudeProjects(jsonAt(entries, "projects.json"))
        val conversations = conversationArray(jsonAt(entries, "conversations.json"))
        val known = projectObjects.mapNotNull { it["uuid"]?.text() }.toSet()
        val result = projectObjects.mapIndexed { index, project ->
            val uuid = project["uuid"]?.text()?.takeIf { it.isNotBlank() } ?: "project-$index"
            val docs = (project["docs"] as? JsonArray).orEmpty()
            val files = docs.mapNotNull { element ->
                val doc = element as? JsonObject ?: return@mapNotNull null
                val filename = doc["filename"]?.text()?.substringAfterLast('/')?.ifBlank { null } ?: return@mapNotNull null
                val content = doc["content"]?.text() ?: return@mapNotNull null
                ImportedFile(
                    relativePath = "files/$filename",
                    displayName = filename,
                    bytes = content.toByteArray(Charsets.UTF_8)
                )
            }
            val chats = conversations.filter { it["project_uuid"]?.text() == uuid }.mapNotNull(::claudeChat)
            ImportedProject(
                importKey = "claude:$uuid",
                name = project["name"]?.text()?.trim()?.ifBlank { null } ?: "Claude",
                instructions = project["prompt_template"]?.text() ?: project["description"]?.text().orEmpty(),
                color = ProjectColors.palette[index % ProjectColors.palette.size],
                source = ImportSource.CLAUDE,
                files = files,
                chats = chats
            )
        }.toMutableList()
        val loose = conversations.filter { conversation ->
            val id = conversation["project_uuid"]?.text()
            id.isNullOrBlank() || id !in known
        }.mapNotNull(::claudeChat)
        if (loose.isNotEmpty() || result.isEmpty()) {
            if (loose.isNotEmpty()) {
                result += ImportedProject(
                    importKey = "claude",
                    name = "Claude",
                    source = ImportSource.CLAUDE,
                    chats = loose
                )
            }
        }
        if (result.isEmpty()) throw ProjectArchiveException(ProjectArchiveFailure.EMPTY)
        return result
    }

    private fun claudeChat(obj: JsonObject): ImportedChat? {
        val id = obj["uuid"]?.text()?.trim()?.ifBlank { null } ?: return null
        val messages = (obj["chat_messages"] as? JsonArray)?.mapNotNull { element ->
            val message = element as? JsonObject ?: return@mapNotNull null
            val role = when (message["sender"]?.text()?.lowercase()) {
                "human", "user" -> ImportedRole.USER
                "assistant" -> ImportedRole.ASSISTANT
                else -> return@mapNotNull null
            }
            val text = claudeText(message).trim()
            if (text.isEmpty()) return@mapNotNull null
            ImportedMessage(role, text, epochSeconds(message["created_at"], 0L))
        }.orEmpty()
        val created = epochSeconds(obj["created_at"], messages.firstOrNull()?.createdAt ?: 0L)
        return ImportedChat(
            externalId = id,
            title = obj["name"]?.text()?.trim()?.ifBlank { null } ?: "Chat",
            createdAt = created,
            updatedAt = epochSeconds(obj["updated_at"], created),
            messages = messages
        )
    }

    private fun claudeText(message: JsonObject): String {
        message["text"]?.text()?.takeIf { it.isNotBlank() }?.let { return it }
        val content = message["content"] ?: return ""
        if (content is JsonPrimitive) return content.text().orEmpty()
        val blocks = content as? JsonArray ?: return ""
        return blocks.mapNotNull { block ->
            when (block) {
                is JsonPrimitive -> block.text()
                is JsonObject -> block["text"]?.text()
                else -> null
            }
        }.filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun parseGrok(entries: List<ArchiveEntry>): ImportedProject {
        val payload = grokPayload(entries) ?: throw ProjectArchiveException(ProjectArchiveFailure.UNRECOGNIZED)
        val conversations = payload["conversations"] as? JsonArray ?: JsonArray(emptyList())
        val parsed = conversations.mapIndexedNotNull { index, element ->
            (element as? JsonObject)?.let { grokConversation(it, index) }
        }
        val custom = payload["customInstructions"]?.text()?.trim().orEmpty()
        val systems = parsed.map { it.system }.filter { it.isNotBlank() }
        val hoisted = if (systems.isNotEmpty() && systems.all { it == systems.first() }) systems.first() else ""
        val sourceId = payload["id"]?.text()?.trim()?.ifBlank { null }
        return ImportedProject(
            importKey = if (sourceId == null) "grok" else "grok:$sourceId",
            name = payload["name"]?.text()?.trim()?.ifBlank { null } ?: "Grok",
            instructions = custom.ifBlank { hoisted },
            source = ImportSource.GROK,
            files = filesUnder(entries),
            chats = parsed.map { it.chat }
        )
    }

    private fun grokConversation(obj: JsonObject, index: Int): ChatWithSystem? {
        val nested = obj["conversation"] as? JsonObject
        val messages = obj["messages"] as? JsonArray
        if (messages != null) {
            val created = epochSeconds(obj["createdAt"], 0L)
            val imported = messages.mapNotNull { element -> grokMessage(element as? JsonObject ?: return@mapNotNull null, created) }
            val system = imported.filter { it.role == null }.joinToString("\n\n") { it.text }
            val id = obj["conversationId"]?.text()?.trim()?.ifBlank { null }
                ?: obj["id"]?.text()?.trim()?.ifBlank { null }
                ?: "grok-$index"
            return ChatWithSystem(
                chat = ImportedChat(
                    externalId = id,
                    title = obj["title"]?.text()?.trim()?.ifBlank { null } ?: "Chat",
                    createdAt = created,
                    updatedAt = epochSeconds(obj["updatedAt"], created),
                    messages = imported.mapNotNull { item ->
                        val role = item.role ?: return@mapNotNull null
                        ImportedMessage(role, item.text, item.createdAt)
                    }
                ),
                system = system
            )
        }
        val responses = obj["responses"] as? JsonArray ?: return null
        val conversation = nested ?: obj
        val created = epochSeconds(conversation["createdAt"] ?: conversation["create_time"], 0L)
        val turns = mutableListOf<ImportedMessage>()
        responses.forEach { element ->
            val response = (element as? JsonObject)?.get("response") as? JsonObject ?: element as? JsonObject ?: return@forEach
            val user = response["user_query"]?.text()?.trim().orEmpty()
            val assistant = response["model_response"]?.text()?.trim().orEmpty()
            val at = epochSeconds(response["created_at"] ?: response["createdAt"], created)
            if (user.isNotEmpty()) turns += ImportedMessage(ImportedRole.USER, user, at)
            if (assistant.isNotEmpty()) turns += ImportedMessage(ImportedRole.ASSISTANT, assistant, at)
        }
        val id = conversation["id"]?.text()?.trim()?.ifBlank { null } ?: "grok-$index"
        return ChatWithSystem(
            chat = ImportedChat(
                externalId = id,
                title = conversation["title"]?.text()?.trim()?.ifBlank { null } ?: "Chat",
                createdAt = created,
                updatedAt = epochSeconds(conversation["updated_at"] ?: conversation["updatedAt"], created),
                messages = turns
            ),
            system = conversation["system_prompt"]?.text()?.trim().orEmpty()
        )
    }

    private data class GrokLine(val role: ImportedRole?, val text: String, val createdAt: Long)

    private fun grokMessage(message: JsonObject, fallback: Long): GrokLine? {
        val text = message["content"]?.text()?.trim()?.ifBlank { null }
            ?: message["text"]?.text()?.trim()?.ifBlank { null }
            ?: return null
        val role = when (message["role"]?.text()?.lowercase()) {
            "user", "human" -> ImportedRole.USER
            "assistant", "model" -> ImportedRole.ASSISTANT
            "system" -> null
            else -> return null
        }
        return GrokLine(role, text, epochSeconds(message["createdAt"], fallback))
    }

    private fun grokPayload(entries: List<ArchiveEntry>): JsonObject? {
        val preferred = entries.firstOrNull { it.path.equals("conversations.json", ignoreCase = true) }
        val candidates = listOfNotNull(preferred) + entries.filter { it != preferred && it.path.endsWith(".json") }
        return candidates.firstNotNullOfOrNull { entry ->
            val obj = parseJson(entry.bytes) as? JsonObject ?: return@firstNotNullOfOrNull null
            if (looksLikeGrok(obj)) obj else null
        }
    }

    private fun looksLikeGrok(obj: JsonObject): Boolean {
        if (obj["mapping"] is JsonObject || obj["chat_messages"] is JsonArray) return false
        if (obj["customInstructions"] != null && obj["conversations"] is JsonArray) return true
        val conversations = obj["conversations"] as? JsonArray ?: return false
        val first = conversations.firstOrNull() as? JsonObject ?: return false
        if (first["messages"] is JsonArray) return true
        return first["responses"] is JsonArray && first["conversation"] is JsonObject
    }

    private fun claudeProjects(element: JsonElement?): List<JsonObject> {
        val array = when (element) {
            is JsonArray -> element
            is JsonObject -> element["projects"] as? JsonArray ?: JsonArray(listOf(element))
            else -> return emptyList()
        }
        return array.mapNotNull { it as? JsonObject }.filter { project ->
            project["uuid"] != null || project["prompt_template"] != null || project["docs"] is JsonArray
        }
    }

    private fun conversationArray(element: JsonElement?): List<JsonObject> = when (element) {
        is JsonArray -> element.mapNotNull { it as? JsonObject }
        is JsonObject -> {
            val nested = element["conversations"] as? JsonArray
            when {
                nested != null -> nested.mapNotNull { it as? JsonObject }
                element["chat_messages"] is JsonArray || element["uuid"] != null -> listOf(element)
                else -> emptyList()
            }
        }
        else -> emptyList()
    }

    private fun filesUnder(entries: List<ArchiveEntry>): List<ImportedFile> = entries.filter { entry ->
        entry.path.startsWith("files/") && entry.bytes.isNotEmpty()
    }.map { entry ->
        ImportedFile(entry.path, entry.path.substringAfterLast('/'), entry.bytes)
    }

    private fun jsonAt(entries: List<ArchiveEntry>, path: String): JsonElement? =
        entries.firstOrNull { it.path.equals(path, ignoreCase = true) }?.bytes?.let(::parseJson)

    private fun textAt(entries: List<ArchiveEntry>, path: String): String =
        entries.firstOrNull { it.path.equals(path, ignoreCase = true) }?.bytes?.toString(Charsets.UTF_8).orEmpty()

    private fun parseJson(bytes: ByteArray): JsonElement? = try {
        json.parseToJsonElement(bytes.toString(Charsets.UTF_8).trim().trimStart('\uFEFF'))
    } catch (_: Exception) {
        null
    }

    private fun JsonElement.text(): String? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> content
        else -> null
    }

    private fun colorOf(element: JsonElement?): Int {
        val raw = element?.text()?.toLongOrNull() ?: return ProjectColors.palette.first()
        return raw.toInt()
    }

    private fun epochSeconds(element: JsonElement?, fallback: Long): Long {
        val primitive = element as? JsonPrimitive ?: return fallback
        val numeric = primitive.content.toDoubleOrNull()
        if (numeric != null) {
            val whole = numeric.toLong()
            return if (whole > 10_000_000_000L) whole / 1000 else whole
        }
        return parseIso(primitive.content) ?: fallback
    }

    private fun parseIso(text: String): Long? = try {
        Instant.parse(text).epochSecond
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(text).toEpochSecond()
        } catch (_: Exception) {
            null
        }
    }

    private fun derivedKey(name: String, chatIds: List<String>, filePaths: List<String>): String {
        val material = (listOf(name) + chatIds.sorted() + filePaths.sorted()).joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        val hex = digest.take(8).joinToString("") { byte -> "%02x".format(byte) }
        return "robin:$hex"
    }
}
