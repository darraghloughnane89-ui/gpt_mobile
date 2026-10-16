package dev.chungjungsoo.gptmobile.data.canvas

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface CanvasStore {
    suspend fun load(): CanvasLibrary
    suspend fun save(library: CanvasLibrary)
}

class FileCanvasStore(
    private val directory: File,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) : CanvasStore {
    override suspend fun load(): CanvasLibrary = withContext(Dispatchers.IO) {
        val file = File(directory, FILE_NAME)
        if (!file.exists()) return@withContext CanvasLibrary()
        val text = runCatching { file.readText() }.getOrDefault("")
        if (text.isBlank()) return@withContext CanvasLibrary()
        runCatching { json.decodeFromString(CanvasLibrary.serializer(), text) }.getOrDefault(CanvasLibrary())
    }

    override suspend fun save(library: CanvasLibrary) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val encoded = json.encodeToString(CanvasLibrary.serializer(), library)
        val destination = File(directory, FILE_NAME)
        val temporary = File(directory, "$FILE_NAME.tmp")
        temporary.writeText(encoded)
        if (destination.exists() && !destination.delete()) {
            destination.writeText(encoded)
            temporary.delete()
            return@withContext
        }
        if (!temporary.renameTo(destination)) {
            destination.writeText(encoded)
            temporary.delete()
        }
    }

    private companion object {
        const val FILE_NAME = "library.json"
    }
}
