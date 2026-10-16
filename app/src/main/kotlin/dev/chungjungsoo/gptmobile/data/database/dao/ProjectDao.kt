package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Project
import dev.chungjungsoo.gptmobile.data.database.entity.ProjectFile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updated_at DESC")
    suspend fun getProjects(): List<Project>

    @Query("SELECT * FROM projects ORDER BY updated_at DESC")
    fun observeProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE project_id = :projectId")
    suspend fun getProject(projectId: Int): Project?

    @Query("SELECT * FROM projects WHERE import_key = :importKey LIMIT 1")
    suspend fun getProjectByImportKey(importKey: String): Project?

    @Query("SELECT * FROM projects WHERE project_id = :projectId")
    fun observeProject(projectId: Int): Flow<Project?>

    @Insert
    suspend fun insertProject(project: Project): Long

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)

    @Query("SELECT * FROM project_files WHERE project_id = :projectId ORDER BY created_at ASC, file_id ASC")
    suspend fun getFiles(projectId: Int): List<ProjectFile>

    @Query("SELECT * FROM project_files WHERE project_id = :projectId ORDER BY created_at ASC, file_id ASC")
    fun observeFiles(projectId: Int): Flow<List<ProjectFile>>

    @Query("SELECT * FROM project_files WHERE file_id = :fileId")
    suspend fun getFile(fileId: Int): ProjectFile?

    @Insert
    suspend fun insertFile(file: ProjectFile): Long

    @Update
    suspend fun updateFile(file: ProjectFile)

    @Delete
    suspend fun deleteFile(file: ProjectFile)

    @Query("SELECT * FROM chats_v2 WHERE project_id = :projectId ORDER BY updated_at DESC")
    fun observeChats(projectId: Int): Flow<List<ChatRoomV2>>

    @Query("SELECT * FROM chats_v2 WHERE project_id = :projectId ORDER BY created_at ASC, chat_id ASC")
    suspend fun getChatsInProject(projectId: Int): List<ChatRoomV2>

    @Query("SELECT * FROM chats_v2 ORDER BY updated_at DESC")
    suspend fun getChats(): List<ChatRoomV2>

    @Query("UPDATE chats_v2 SET project_id = NULL WHERE project_id = :projectId")
    suspend fun clearChatsInProject(projectId: Int)

    @Query("UPDATE chats_v2 SET project_id = :projectId WHERE chat_id IN (:chatIds)")
    suspend fun moveChats(chatIds: List<Int>, projectId: Int?)
}
