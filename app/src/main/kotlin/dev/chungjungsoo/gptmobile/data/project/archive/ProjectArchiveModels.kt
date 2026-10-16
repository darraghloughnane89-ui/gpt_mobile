package dev.chungjungsoo.gptmobile.data.project.archive

import dev.chungjungsoo.gptmobile.data.project.ProjectColors
import dev.chungjungsoo.gptmobile.data.project.ProjectIcons

enum class ImportSource {
    ROBIN,
    CHATGPT,
    CLAUDE,
    GROK
}

enum class ImportedRole {
    USER,
    ASSISTANT
}

enum class ProjectArchiveFailure {
    UNRECOGNIZED,
    UNREADABLE,
    EMPTY,
    TOO_LARGE
}

enum class ProjectTransferStage {
    READING,
    PARSING,
    WRITING,
    EXPORTING
}

data class ArchiveEntry(
    val path: String,
    val bytes: ByteArray
)

data class ImportedMessage(
    val role: ImportedRole,
    val text: String,
    val createdAt: Long
)

data class ImportedChat(
    val externalId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ImportedMessage>
)

data class ImportedFile(
    val relativePath: String,
    val displayName: String,
    val bytes: ByteArray,
    val mimeType: String? = null
)

data class ImportedProject(
    val importKey: String,
    val name: String,
    val instructions: String = "",
    val icon: String = ProjectIcons.FOLDER,
    val color: Int = ProjectColors.palette.first(),
    val source: ImportSource,
    val files: List<ImportedFile> = emptyList(),
    val chats: List<ImportedChat> = emptyList()
)

data class ProjectTransferProgress(
    val stage: ProjectTransferStage,
    val completed: Int,
    val total: Int,
    val label: String = ""
)

data class ProjectArchiveResult(
    val projectIds: List<Int>,
    val createdProjects: Int,
    val updatedProjects: Int,
    val chatCount: Int,
    val fileCount: Int
)

sealed class ProjectArchiveOutcome {
    data class Success(val result: ProjectArchiveResult) : ProjectArchiveOutcome()
    data class Failure(val failure: ProjectArchiveFailure) : ProjectArchiveOutcome()
}

class ProjectArchiveException(val failure: ProjectArchiveFailure) : Exception()

data class ArchiveProjectRecord(
    val id: Int,
    val name: String,
    val icon: String,
    val color: Int,
    val instructions: String,
    val importKey: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class ArchiveChatRecord(
    val id: Int,
    val projectId: Int,
    val title: String,
    val enabledPlatforms: List<String>,
    val createdAt: Long,
    val updatedAt: Long
)

data class ArchiveFileRecord(
    val id: Int,
    val projectId: Int,
    val displayName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val extractedText: String?
)

data class ArchiveStoredMessage(
    val id: Int,
    val chatId: Int,
    val text: String,
    val createdAt: Long,
    val platformType: String?,
    val linkedMessageId: Int
)

interface ProjectArchiveSink {
    suspend fun findByImportKey(importKey: String): ArchiveProjectRecord?
    suspend fun insertProject(
        name: String,
        icon: String,
        color: Int,
        instructions: String,
        importKey: String,
        createdAt: Long,
        updatedAt: Long
    ): ArchiveProjectRecord

    suspend fun updateProject(record: ArchiveProjectRecord)
    suspend fun readMap(projectId: Int): ProjectImportMap
    suspend fun writeMap(projectId: Int, map: ProjectImportMap)
    suspend fun getChat(chatId: Int): ArchiveChatRecord?
    suspend fun insertChat(
        projectId: Int,
        title: String,
        enabledPlatforms: List<String>,
        createdAt: Long,
        updatedAt: Long
    ): ArchiveChatRecord

    suspend fun updateChat(record: ArchiveChatRecord)
    suspend fun replaceMessages(chatId: Int, messages: List<ImportedMessage>, assistantPlatformUid: String)
    suspend fun getFile(fileId: Int): ArchiveFileRecord?
    suspend fun insertFile(
        projectId: Int,
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
        extractedText: String?
    ): ArchiveFileRecord

    suspend fun replaceFile(record: ArchiveFileRecord, displayName: String, mimeType: String, bytes: ByteArray, extractedText: String?)
    suspend fun enabledPlatformUids(): List<String>
}
