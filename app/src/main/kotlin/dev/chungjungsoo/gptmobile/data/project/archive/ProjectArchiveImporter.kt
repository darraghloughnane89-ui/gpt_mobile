package dev.chungjungsoo.gptmobile.data.project.archive

import dev.chungjungsoo.gptmobile.data.project.ProjectContextLimits
import dev.chungjungsoo.gptmobile.data.project.ProjectIcons
import kotlinx.coroutines.CancellationException

object ProjectArchiveImporter {
    suspend fun import(
        projects: List<ImportedProject>,
        sink: ProjectArchiveSink,
        options: ProjectImportOptions = ProjectImportOptions(),
        imageProcessor: ProjectImageProcessor = ProjectImageProcessor.Identity,
        isActive: () -> Boolean = { true },
        onProgress: (ProjectTransferProgress) -> Unit = {}
    ): ProjectArchiveResult {
        var created = 0
        var updated = 0
        var chatCount = 0
        var fileCount = 0
        var imageCount = 0
        val ids = mutableListOf<Int>()
        val refs = mutableListOf<ArchivedProjectRef>()
        val skipped = mutableListOf<ImportSkip>()
        val now = System.currentTimeMillis() / 1000
        val enabled = sink.enabledPlatformUids()
        projects.forEachIndexed { index, project ->
            if (!isActive()) throw CancellationException("Import cancelled")
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
            val createdThis = existing == null
            refs += ArchivedProjectRef(record.id, name, createdThis, project.source)
            val map = sink.readMap(record.id)
            val chatMap = map.chats.toMutableMap()
            val fileMap = map.files.toMutableMap()
            val ingested = ingestFiles(
                projectId = record.id,
                files = project.files,
                fileMap = fileMap,
                sink = sink,
                options = options,
                imageProcessor = imageProcessor,
                isActive = isActive
            )
            fileCount += ingested.fileCount
            imageCount += ingested.imageCount
            skipped += ingested.skipped
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
            fileCount = fileCount,
            imageCount = imageCount,
            skipped = skipped,
            projects = refs
        )
    }

    internal suspend fun ingestFiles(
        projectId: Int,
        files: List<ImportedFile>,
        fileMap: MutableMap<String, Int>,
        sink: ProjectArchiveSink,
        options: ProjectImportOptions = ProjectImportOptions(),
        imageProcessor: ProjectImageProcessor = ProjectImageProcessor.Identity,
        isActive: () -> Boolean = { true }
    ): FileIngestResult {
        var fileCount = 0
        var imageCount = 0
        val skipped = mutableListOf<ImportSkip>()
        files.forEach { file ->
            if (!isActive()) throw CancellationException("Import cancelled")
            val intake = ProjectFileIntake.prepare(file, options, imageProcessor)
            skipped += intake.skipped
            intake.files.forEach { prepared ->
                val stored = fileMap[prepared.relativePath]?.let { sink.getFile(it) }?.takeIf { it.projectId == projectId }
                val displayName = prepared.displayName.take(180)
                if (stored == null) {
                    val inserted = sink.insertFile(
                        projectId = projectId,
                        displayName = displayName,
                        mimeType = prepared.mimeType,
                        bytes = prepared.bytes,
                        extractedText = prepared.extractedText
                    )
                    fileMap[prepared.relativePath] = inserted.id
                } else if (!stored.bytes.contentEquals(prepared.bytes) || stored.extractedText != prepared.extractedText) {
                    sink.replaceFile(
                        record = stored,
                        displayName = displayName,
                        mimeType = prepared.mimeType,
                        bytes = prepared.bytes,
                        extractedText = prepared.extractedText
                    )
                }
                fileCount++
                if (prepared.image) imageCount++
            }
        }
        return FileIngestResult(fileCount = fileCount, imageCount = imageCount, skipped = skipped)
    }
}

internal data class FileIngestResult(
    val fileCount: Int,
    val imageCount: Int,
    val skipped: List<ImportSkip>
)
