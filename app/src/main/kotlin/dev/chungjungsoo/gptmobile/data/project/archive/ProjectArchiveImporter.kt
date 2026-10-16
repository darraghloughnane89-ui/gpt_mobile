package dev.chungjungsoo.gptmobile.data.project.archive

import dev.chungjungsoo.gptmobile.data.project.PdfTextExtractor
import dev.chungjungsoo.gptmobile.data.project.ProjectContextLimits
import dev.chungjungsoo.gptmobile.data.project.ProjectFileKind
import dev.chungjungsoo.gptmobile.data.project.ProjectFileTypes
import dev.chungjungsoo.gptmobile.data.project.ProjectIcons

object ProjectArchiveImporter {
    suspend fun import(
        projects: List<ImportedProject>,
        sink: ProjectArchiveSink,
        onProgress: (ProjectTransferProgress) -> Unit = {}
    ): ProjectArchiveResult {
        var created = 0
        var updated = 0
        var chatCount = 0
        var fileCount = 0
        val ids = mutableListOf<Int>()
        val now = System.currentTimeMillis() / 1000
        val enabled = sink.enabledPlatformUids()
        projects.forEachIndexed { index, project ->
            onProgress(ProjectTransferProgress(ProjectTransferStage.WRITING, index, projects.size, project.name))
            val name = project.name.trim().take(ProjectContextLimits.MAX_NAME_CHARS).ifBlank { "Imported project" }
            val instructions = project.instructions.trim().take(ProjectContextLimits.MAX_INSTRUCTION_CHARS)
            val icon = if (project.icon in ProjectIcons.all) project.icon else ProjectIcons.FOLDER
            val existing = sink.findByImportKey(project.importKey)
            val record = if (existing == null) {
                created++
                sink.insertProject(
                    name = name,
                    icon = icon,
                    color = project.color,
                    instructions = instructions,
                    importKey = project.importKey,
                    createdAt = now,
                    updatedAt = now
                )
            } else {
                updated++
                val revised = existing.copy(
                    name = name,
                    icon = icon,
                    color = project.color,
                    instructions = instructions,
                    updatedAt = now
                )
                sink.updateProject(revised)
                revised
            }
            ids += record.id
            val map = sink.readMap(record.id)
            val chatMap = map.chats.toMutableMap()
            val fileMap = map.files.toMutableMap()
            project.files.forEach { file ->
                if (file.bytes.isEmpty() || file.bytes.size > ProjectContextLimits.MAX_STORED_BYTES) return@forEach
                val kind = ProjectFileTypes.classify(file.mimeType, file.displayName) ?: return@forEach
                val extracted = extract(kind, file.bytes)
                val mime = file.mimeType?.substringBefore(';')?.trim()?.ifBlank { null } ?: defaultMime(file.displayName, kind)
                val stored = fileMap[file.relativePath]?.let { sink.getFile(it) }?.takeIf { it.projectId == record.id }
                if (stored == null) {
                    val inserted = sink.insertFile(
                        projectId = record.id,
                        displayName = file.displayName.take(180),
                        mimeType = mime,
                        bytes = file.bytes,
                        extractedText = extracted
                    )
                    fileMap[file.relativePath] = inserted.id
                    fileCount++
                } else if (!stored.bytes.contentEquals(file.bytes)) {
                    sink.replaceFile(
                        record = stored,
                        displayName = file.displayName.take(180),
                        mimeType = mime,
                        bytes = file.bytes,
                        extractedText = extracted
                    )
                    fileCount++
                }
            }
            project.chats.forEach { chat ->
                val stored = chatMap[chat.externalId]?.let { sink.getChat(it) }?.takeIf { it.projectId == record.id }
                val chatRecord = if (stored == null) {
                    val inserted = sink.insertChat(
                        projectId = record.id,
                        title = chat.title.trim().take(120).ifBlank { "Imported chat" },
                        enabledPlatforms = enabled,
                        createdAt = chat.createdAt.takeIf { it > 0 } ?: now,
                        updatedAt = chat.updatedAt.takeIf { it > 0 } ?: now
                    )
                    chatMap[chat.externalId] = inserted.id
                    inserted
                } else {
                    val revised = stored.copy(
                        title = chat.title.trim().take(120).ifBlank { stored.title },
                        updatedAt = chat.updatedAt.takeIf { it > 0 } ?: now
                    )
                    sink.updateChat(revised)
                    revised
                }
                val assistantUid = chatRecord.enabledPlatforms.firstOrNull() ?: enabled.firstOrNull() ?: "imported"
                sink.replaceMessages(chatRecord.id, chat.messages, assistantUid)
                chatCount++
            }
            sink.writeMap(record.id, ProjectImportMap(chats = chatMap, files = fileMap))
        }
        onProgress(ProjectTransferProgress(ProjectTransferStage.WRITING, projects.size, projects.size, ""))
        return ProjectArchiveResult(
            projectIds = ids,
            createdProjects = created,
            updatedProjects = updated,
            chatCount = chatCount,
            fileCount = fileCount
        )
    }

    private fun extract(kind: ProjectFileKind, bytes: ByteArray): String? = when (kind) {
        ProjectFileKind.TEXT -> bytes.toString(Charsets.UTF_8)
            .trim()
            .take(ProjectContextLimits.MAX_FILE_CHARS)
            .ifBlank { null }
        ProjectFileKind.PDF -> PdfTextExtractor.extract(bytes, ProjectContextLimits.MAX_FILE_CHARS).ifBlank { null }
        ProjectFileKind.IMAGE -> null
    }

    private fun defaultMime(displayName: String, kind: ProjectFileKind): String {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "md", "markdown" -> "text/markdown"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> when (kind) {
                ProjectFileKind.PDF -> "application/pdf"
                ProjectFileKind.IMAGE -> "image/*"
                ProjectFileKind.TEXT -> "text/plain"
            }
        }
    }
}
