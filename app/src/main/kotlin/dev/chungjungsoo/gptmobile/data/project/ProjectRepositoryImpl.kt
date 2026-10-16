package dev.chungjungsoo.gptmobile.data.project

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.database.dao.ProjectDao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Project
import dev.chungjungsoo.gptmobile.data.database.entity.ProjectFile
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProjectRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val projectDao: ProjectDao,
    private val fileStore: ProjectFileStore
) : ProjectRepository {
    override suspend fun listProjects(): List<Project> = projectDao.getProjects()

    override suspend fun moveChats(chatIds: List<Int>, projectId: Int?) {
        if (chatIds.isEmpty()) return
        projectDao.moveChats(chatIds, projectId)
    }

    override suspend fun getProject(projectId: Int): Project? = projectDao.getProject(projectId)

    override fun observeProject(projectId: Int): Flow<Project?> = projectDao.observeProject(projectId)

    override fun observeProjects(): Flow<List<Project>> = projectDao.observeProjects()

    override suspend fun createProject(name: String, icon: String, color: Int, instructions: String): Project {
        val now = System.currentTimeMillis() / 1000
        val project = Project(
            name = name.trim().take(ProjectContextLimits.MAX_NAME_CHARS),
            icon = icon,
            color = color,
            instructions = instructions.trim().take(ProjectContextLimits.MAX_INSTRUCTION_CHARS),
            createdAt = now,
            updatedAt = now
        )
        val id = projectDao.insertProject(project).toInt()
        return project.copy(id = id)
    }

    override suspend fun updateProject(project: Project) {
        projectDao.updateProject(
            project.copy(
                name = project.name.trim().take(ProjectContextLimits.MAX_NAME_CHARS),
                instructions = project.instructions.trim().take(ProjectContextLimits.MAX_INSTRUCTION_CHARS),
                updatedAt = System.currentTimeMillis() / 1000
            )
        )
    }

    override suspend fun deleteProject(projectId: Int) {
        val project = projectDao.getProject(projectId) ?: return
        val files = projectDao.getFiles(projectId)
        projectDao.clearChatsInProject(projectId)
        projectDao.deleteProject(project)
        files.forEach { file -> fileStore.delete(projectId, file.storageName) }
        fileStore.deleteProject(projectId)
    }

    override suspend fun getFiles(projectId: Int): List<ProjectFile> = projectDao.getFiles(projectId)

    override fun observeFiles(projectId: Int): Flow<List<ProjectFile>> = projectDao.observeFiles(projectId)

    override fun observeChats(projectId: Int): Flow<List<ChatRoomV2>> = projectDao.observeChats(projectId)

    override suspend fun listChats(): List<ChatRoomV2> = projectDao.getChats()

    override suspend fun importFile(projectId: Int, uri: Uri): ProjectImportError? = withContext(Dispatchers.IO) {
        try {
            importFileUnchecked(projectId, uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ProjectImportError.UNREADABLE
        }
    }

    private suspend fun importFileUnchecked(projectId: Int, uri: Uri): ProjectImportError? {
        if (projectDao.getProject(projectId) == null) return ProjectImportError.UNREADABLE
        val displayName = displayName(uri)
        val mimeType = context.contentResolver.getType(uri)
        val kind = ProjectFileTypes.classify(mimeType, displayName) ?: return ProjectImportError.UNSUPPORTED
        val bytes = readLimited(uri) ?: return ProjectImportError.TOO_LARGE
        if (bytes.isEmpty()) return ProjectImportError.EMPTY
        val extracted = extract(kind, bytes)
        val storageName = "${UUID.randomUUID()}_${sanitize(displayName)}"
        fileStore.write(projectId, storageName, bytes)
        projectDao.insertFile(
            ProjectFile(
                projectId = projectId,
                displayName = displayName.take(180),
                mimeType = mimeType?.substringBefore(';')?.trim()?.ifBlank { null } ?: kind.name.lowercase(),
                storageName = storageName,
                extractedText = extracted,
                sizeBytes = bytes.size.toLong()
            )
        )
        touchProject(projectId)
        return null
    }

    override suspend fun deleteFile(file: ProjectFile) {
        projectDao.deleteFile(file)
        fileStore.delete(file.projectId, file.storageName)
        touchProject(file.projectId)
    }

    private suspend fun touchProject(projectId: Int) {
        val project = projectDao.getProject(projectId) ?: return
        projectDao.updateProject(project.copy(updatedAt = System.currentTimeMillis() / 1000))
    }

    private fun extract(kind: ProjectFileKind, bytes: ByteArray): String? = when (kind) {
        ProjectFileKind.TEXT -> bytes.toString(Charsets.UTF_8)
            .trim()
            .take(ProjectContextLimits.MAX_FILE_CHARS)
            .ifBlank { null }
        ProjectFileKind.PDF -> PdfTextExtractor.extract(bytes, ProjectContextLimits.MAX_FILE_CHARS).ifBlank { null }
        ProjectFileKind.IMAGE -> null
    }

    private fun displayName(uri: Uri): String {
        val queried = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        return queried?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "file"
    }

    private fun readLimited(uri: Uri): ByteArray? {
        val input = context.contentResolver.openInputStream(uri) ?: return ByteArray(0)
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > ProjectContextLimits.MAX_STORED_BYTES) return null
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').take(80)
        return cleaned.ifBlank { "file" }
    }
}
