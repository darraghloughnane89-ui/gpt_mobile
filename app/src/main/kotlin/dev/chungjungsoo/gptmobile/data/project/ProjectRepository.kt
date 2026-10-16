package dev.chungjungsoo.gptmobile.data.project

import android.net.Uri
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Project
import dev.chungjungsoo.gptmobile.data.database.entity.ProjectFile
import kotlinx.coroutines.flow.Flow

interface ProjectChatListSource {
    suspend fun listProjects(): List<Project>
    suspend fun moveChats(chatIds: List<Int>, projectId: Int?)
}

interface ProjectRepository : ProjectChatListSource {
    suspend fun getProject(projectId: Int): Project?
    fun observeProject(projectId: Int): Flow<Project?>
    fun observeProjects(): Flow<List<Project>>
    suspend fun createProject(name: String, icon: String, color: Int, instructions: String): Project
    suspend fun updateProject(project: Project)
    suspend fun deleteProject(projectId: Int)
    suspend fun getFiles(projectId: Int): List<ProjectFile>
    fun observeFiles(projectId: Int): Flow<List<ProjectFile>>
    fun observeChats(projectId: Int): Flow<List<ChatRoomV2>>
    suspend fun listChats(): List<ChatRoomV2>
    suspend fun importFile(projectId: Int, uri: Uri): ProjectImportError?
    suspend fun deleteFile(file: ProjectFile)
}

enum class ProjectImportError {
    TOO_LARGE,
    UNSUPPORTED,
    UNREADABLE,
    EMPTY
}
