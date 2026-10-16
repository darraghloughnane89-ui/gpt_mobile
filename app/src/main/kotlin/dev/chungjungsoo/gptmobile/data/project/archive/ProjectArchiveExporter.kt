package dev.chungjungsoo.gptmobile.data.project.archive

import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.Project
import dev.chungjungsoo.gptmobile.data.database.entity.ProjectFile

data class AssembledArchive(
    val project: ImportedProject,
    val map: ProjectImportMap
)

object ProjectArchiveExporter {
    fun assemble(
        project: Project,
        importKey: String,
        files: List<Pair<ProjectFile, ByteArray>>,
        chats: List<Pair<ChatRoomV2, List<MessageV2>>>,
        map: ProjectImportMap
    ): AssembledArchive {
        val fileRecords = files.map { it.first }
        val chatRecords = chats.map { it.first }
        val filePaths = usedPaths(map.files, fileRecords).filterKeys { id -> fileRecords.any { it.id == id } }
        val chatIds = usedChatIds(map.chats, chatRecords).filterKeys { id -> chatRecords.any { it.id == id } }
        val exported = ImportedProject(
            importKey = importKey,
            name = project.name,
            instructions = project.instructions,
            icon = project.icon,
            color = project.color,
            source = ImportSource.ROBIN,
            files = files.mapNotNull { (file, bytes) ->
                if (bytes.isEmpty()) return@mapNotNull null
                val path = filePaths[file.id] ?: return@mapNotNull null
                ImportedFile(
                    relativePath = path,
                    displayName = file.displayName,
                    bytes = bytes,
                    mimeType = file.mimeType
                )
            },
            chats = chats.map { (chat, messages) ->
                ImportedChat(
                    externalId = chatIds.getValue(chat.id),
                    title = chat.title,
                    createdAt = chat.createdAt,
                    updatedAt = chat.updatedAt,
                    messages = messages.mapNotNull { message ->
                        val text = message.content.trim()
                        if (text.isEmpty()) return@mapNotNull null
                        ImportedMessage(
                            role = if (message.platformType == null) ImportedRole.USER else ImportedRole.ASSISTANT,
                            text = text,
                            createdAt = message.createdAt
                        )
                    }
                )
            }
        )
        return AssembledArchive(
            project = exported,
            map = ProjectImportMap(
                chats = chatIds.entries.associate { (id, externalId) -> externalId to id },
                files = filePaths.entries.associate { (id, path) -> path to id }
            )
        )
    }

    private fun usedPaths(mapped: Map<String, Int>, files: List<ProjectFile>): Map<Int, String> {
        val reverse = mapped.entries.associate { (path, id) -> id to path }.toMutableMap()
        val used = reverse.values.toMutableSet()
        files.forEach { file ->
            if (file.id in reverse) return@forEach
            var candidate = "files/${safe(file.displayName)}"
            var suffix = 2
            while (candidate in used) {
                val dot = candidate.lastIndexOf('.')
                candidate = if (dot > "files/".length) {
                    candidate.substring(0, dot) + "-$suffix" + candidate.substring(dot)
                } else {
                    "$candidate-$suffix"
                }
                suffix++
            }
            reverse[file.id] = candidate
            used += candidate
        }
        return reverse
    }

    private fun usedChatIds(mapped: Map<String, Int>, chats: List<ChatRoomV2>): Map<Int, String> {
        val reverse = mapped.entries.associate { (externalId, id) -> id to externalId }.toMutableMap()
        chats.forEach { chat ->
            if (chat.id !in reverse) reverse[chat.id] = "chat-${chat.id}"
        }
        return reverse
    }

    private fun safe(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').take(80)
        return cleaned.ifBlank { "file" }
    }
}
