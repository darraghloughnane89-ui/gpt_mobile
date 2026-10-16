package dev.chungjungsoo.gptmobile.data.project

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class ProjectFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun write(projectId: Int, storageName: String, bytes: ByteArray) {
        file(projectId, storageName).writeBytes(bytes)
    }

    fun read(projectId: Int, storageName: String): ByteArray? {
        val target = file(projectId, storageName)
        return if (target.isFile) target.readBytes() else null
    }

    fun readText(projectId: Int, storageName: String): String? = read(projectId, storageName)?.toString(Charsets.UTF_8)

    fun writeText(projectId: Int, storageName: String, text: String) {
        write(projectId, storageName, text.toByteArray(Charsets.UTF_8))
    }

    fun delete(projectId: Int, storageName: String) {
        file(projectId, storageName).delete()
    }

    fun deleteProject(projectId: Int) {
        File(context.filesDir, "projects/$projectId").deleteRecursively()
    }

    private fun file(projectId: Int, storageName: String): File {
        val directory = File(context.filesDir, "projects/$projectId")
        directory.mkdirs()
        val safeName = storageName.substringAfterLast('/').substringAfterLast('\\')
        return File(directory, safeName)
    }
}
