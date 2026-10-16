package dev.chungjungsoo.gptmobile.data.prompt

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FilePromptLibraryStorage(
    private val file: File
) : PromptLibraryStorage {
    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (!file.exists()) null else file.readText()
    }

    override suspend fun write(contents: String) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(contents)
        if (!temporary.renameTo(file)) {
            file.writeText(contents)
            temporary.delete()
        }
    }
}
