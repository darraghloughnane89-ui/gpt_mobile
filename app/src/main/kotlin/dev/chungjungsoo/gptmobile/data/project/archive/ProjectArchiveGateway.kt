package dev.chungjungsoo.gptmobile.data.project.archive

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.ProjectDao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.Project
import dev.chungjungsoo.gptmobile.data.database.entity.ProjectFile
import dev.chungjungsoo.gptmobile.data.project.ProjectFileStore
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ProjectArchiveGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val projectDao: ProjectDao,
    private val chatRoomDao: ChatRoomV2Dao,
    private val messageDao: MessageV2Dao,
    private val fileStore: ProjectFileStore,
    private val settingRepository: SettingRepository
) {
    private val sink = RoomProjectArchiveSink(
        projectDao = projectDao,
        chatRoomDao = chatRoomDao,
        messageDao = messageDao,
        fileStore = fileStore,
        enabledPlatforms = { settingRepository.fetchPlatformV2s().filter { it.enabled }.map { it.uid } }
    )

    suspend fun importZip(
        uri: Uri,
        options: ProjectImportOptions = ProjectImportOptions(),
        onProgress: (ProjectTransferProgress) -> Unit = {}
    ): ProjectArchiveOutcome = guarded {
        val active = coroutineContext
        ProjectArchiveSession.import(
            open = {
                context.contentResolver.openInputStream(uri)
                    ?: throw ProjectArchiveException(ProjectArchiveFailure.UNREADABLE)
            },
            sink = sink,
            options = options,
            imageProcessor = AndroidProjectImageProcessor(),
            onProgress = onProgress,
            isActive = { active.isActive }
        )
    }

    suspend fun importFolder(
        uri: Uri,
        options: ProjectImportOptions = ProjectImportOptions(),
        onProgress: (ProjectTransferProgress) -> Unit = {}
    ): ProjectArchiveOutcome = guarded {
        val active = coroutineContext
        val staged = ProjectFolderStage.stage(context, uri, onProgress, isActive = { active.isActive })
        try {
            ProjectArchiveSession.importStaged(
                staged = staged,
                sink = sink,
                options = options,
                imageProcessor = AndroidProjectImageProcessor(),
                onProgress = onProgress,
                isActive = { active.isActive }
            )
        } finally {
            staged.close()
        }
    }

    suspend fun exportProject(projectId: Int, uri: Uri, onProgress: (ProjectTransferProgress) -> Unit = {}): ProjectArchiveOutcome =
        guarded {
            onProgress(ProjectTransferProgress(ProjectTransferStage.EXPORTING, 0, 1))
            val project = projectDao.getProject(projectId) ?: throw ProjectArchiveException(ProjectArchiveFailure.UNREADABLE)
            val key = project.importKey?.takeIf { it.isNotBlank() } ?: "robin:${UUID.randomUUID()}"
            if (project.importKey != key) {
                projectDao.updateProject(project.copy(importKey = key, updatedAt = System.currentTimeMillis() / 1000))
            }
            val files = projectDao.getFiles(projectId).mapNotNull { file ->
                val bytes = fileStore.read(projectId, file.storageName) ?: return@mapNotNull null
                file to bytes
            }
            val chats = projectDao.getChatsInProject(projectId).map { chat ->
                chat to messageDao.loadMessages(chat.id)
            }
            val assembled = ProjectArchiveExporter.assemble(
                project = project.copy(importKey = key),
                importKey = key,
                files = files,
                chats = chats,
                map = sink.readMap(projectId)
            )
            sink.writeMap(projectId, assembled.map)
            val bytes = ProjectArchiveZip.write(assembled.project)
            val stream = context.contentResolver.openOutputStream(uri)
                ?: throw ProjectArchiveException(ProjectArchiveFailure.UNREADABLE)
            stream.use { it.write(bytes) }
            onProgress(ProjectTransferProgress(ProjectTransferStage.EXPORTING, 1, 1, project.name))
            ProjectArchiveResult(
                projectIds = listOf(projectId),
                createdProjects = 0,
                updatedProjects = 1,
                chatCount = assembled.project.chats.size,
                fileCount = assembled.project.files.size
            )
        }

    private suspend fun guarded(
        block: suspend () -> ProjectArchiveResult
    ): ProjectArchiveOutcome = withContext(Dispatchers.IO) {
        try {
            ProjectArchiveOutcome.Success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (archive: ProjectArchiveException) {
            ProjectArchiveOutcome.Failure(archive.failure)
        } catch (_: Exception) {
            ProjectArchiveOutcome.Failure(ProjectArchiveFailure.UNREADABLE)
        }
    }
}

private class RoomProjectArchiveSink(
    private val projectDao: ProjectDao,
    private val chatRoomDao: ChatRoomV2Dao,
    private val messageDao: MessageV2Dao,
    private val fileStore: ProjectFileStore,
    private val enabledPlatforms: suspend () -> List<String>
) : ProjectArchiveSink {
    override suspend fun findByImportKey(importKey: String): ArchiveProjectRecord? =
        projectDao.getProjectByImportKey(importKey)?.toRecord()

    override suspend fun insertProject(
        name: String,
        icon: String,
        color: Int,
        instructions: String,
        importKey: String,
        createdAt: Long,
        updatedAt: Long
    ): ArchiveProjectRecord {
        val project = Project(
            name = name,
            icon = icon,
            color = color,
            instructions = instructions,
            createdAt = createdAt,
            updatedAt = updatedAt,
            importKey = importKey
        )
        val id = projectDao.insertProject(project).toInt()
        return project.copy(id = id).toRecord()
    }

    override suspend fun updateProject(record: ArchiveProjectRecord) {
        val existing = projectDao.getProject(record.id) ?: return
        projectDao.updateProject(
            existing.copy(
                name = record.name,
                icon = record.icon,
                color = record.color,
                instructions = record.instructions,
                updatedAt = record.updatedAt,
                importKey = record.importKey
            )
        )
    }

    override suspend fun readMap(projectId: Int): ProjectImportMap {
        val text = fileStore.readText(projectId, MAP_FILE) ?: return ProjectImportMap()
        return try {
            mapJson.decodeFromString(ProjectImportMap.serializer(), text)
        } catch (_: Exception) {
            ProjectImportMap()
        }
    }

    override suspend fun writeMap(projectId: Int, map: ProjectImportMap) {
        fileStore.writeText(projectId, MAP_FILE, mapJson.encodeToString(ProjectImportMap.serializer(), map))
    }

    override suspend fun getChat(chatId: Int): ArchiveChatRecord? = chatRoomDao.getChatRoom(chatId)?.toRecord()

    override suspend fun insertChat(
        projectId: Int,
        title: String,
        enabledPlatforms: List<String>,
        createdAt: Long,
        updatedAt: Long
    ): ArchiveChatRecord {
        val chat = ChatRoomV2(
            title = title,
            enabledPlatform = enabledPlatforms,
            createdAt = createdAt,
            updatedAt = updatedAt,
            projectId = projectId
        )
        val id = chatRoomDao.addChatRoom(chat).toInt()
        return chat.copy(id = id).toRecord()
    }

    override suspend fun updateChat(record: ArchiveChatRecord) {
        val existing = chatRoomDao.getChatRoom(record.id) ?: return
        chatRoomDao.editChatRoom(
            existing.copy(
                title = record.title,
                updatedAt = record.updatedAt,
                projectId = record.projectId
            )
        )
    }

    override suspend fun replaceMessages(chatId: Int, messages: List<ImportedMessage>, assistantPlatformUid: String) {
        ImportedConversation.persist(
            messages = messages,
            assistantPlatformUid = assistantPlatformUid,
            clear = { messageDao.deleteMessagesForChat(chatId) },
            insert = { text, createdAt, platformType, linkedMessageId ->
                messageDao.insertMessage(
                    MessageV2(
                        chatId = chatId,
                        content = text,
                        linkedMessageId = linkedMessageId,
                        platformType = platformType,
                        createdAt = createdAt
                    )
                ).toInt()
            }
        )
    }

    override suspend fun getFile(fileId: Int): ArchiveFileRecord? {
        val file = projectDao.getFile(fileId) ?: return null
        val bytes = fileStore.read(file.projectId, file.storageName) ?: ByteArray(0)
        return file.toRecord(bytes)
    }

    override suspend fun insertFile(
        projectId: Int,
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
        extractedText: String?
    ): ArchiveFileRecord {
        val storageName = "${UUID.randomUUID()}_${sanitize(displayName)}"
        fileStore.write(projectId, storageName, bytes)
        val file = ProjectFile(
            projectId = projectId,
            displayName = displayName,
            mimeType = mimeType,
            storageName = storageName,
            extractedText = extractedText,
            sizeBytes = bytes.size.toLong()
        )
        val id = projectDao.insertFile(file).toInt()
        return file.copy(id = id).toRecord(bytes)
    }

    override suspend fun replaceFile(
        record: ArchiveFileRecord,
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
        extractedText: String?
    ) {
        val existing = projectDao.getFile(record.id) ?: return
        fileStore.write(existing.projectId, existing.storageName, bytes)
        projectDao.updateFile(
            existing.copy(
                displayName = displayName,
                mimeType = mimeType,
                extractedText = extractedText,
                sizeBytes = bytes.size.toLong()
            )
        )
    }

    override suspend fun enabledPlatformUids(): List<String> = enabledPlatforms()

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').take(80)
        return cleaned.ifBlank { "file" }
    }

    private companion object {
        const val MAP_FILE = "import-map.json"
        val mapJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

private fun Project.toRecord() = ArchiveProjectRecord(
    id = id,
    name = name,
    icon = icon,
    color = color,
    instructions = instructions,
    importKey = importKey.orEmpty(),
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun ChatRoomV2.toRecord() = ArchiveChatRecord(
    id = id,
    projectId = projectId ?: 0,
    title = title,
    enabledPlatforms = enabledPlatform,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun ProjectFile.toRecord(bytes: ByteArray) = ArchiveFileRecord(
    id = id,
    projectId = projectId,
    displayName = displayName,
    mimeType = mimeType,
    bytes = bytes,
    extractedText = extractedText
)
