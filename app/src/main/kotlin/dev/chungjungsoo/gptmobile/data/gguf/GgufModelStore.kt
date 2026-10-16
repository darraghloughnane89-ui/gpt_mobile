package dev.chungjungsoo.gptmobile.data.gguf

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class GgufIndex(
    val models: List<GgufModelRecord> = emptyList()
)

@Singleton
class GgufModelStore {
    private val root: File
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutex = Mutex()

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "gguf"))

    constructor(root: File) {
        this.root = root
    }

    suspend fun list(): List<GgufModelRecord> = withContext(Dispatchers.IO) {
        mutex.withLock { readIndex().models.sortedBy { it.displayName.lowercase() } }
    }

    suspend fun get(id: String): GgufModelRecord? = withContext(Dispatchers.IO) {
        mutex.withLock { readIndex().models.firstOrNull { it.id == id } }
    }

    fun fileFor(record: GgufModelRecord): File = File(filesDir(), record.fileName)

    suspend fun importStream(
        displayName: String,
        sourceLabel: String?,
        input: InputStream,
        sizeHint: Long,
        totalMemBytes: Long
    ): GgufModelRecord = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString().replace("-", "")
        val safeName = "$id.gguf"
        filesDir().mkdirs()
        val destination = File(filesDir(), safeName)
        try {
            input.use { source -> destination.outputStream().use { output -> source.copyTo(output) } }
            if (!GgufMetadata.isGguf(destination)) {
                destination.delete()
                error("That file is not a GGUF model")
            }
            val size = destination.length().takeIf { it > 0L } ?: sizeHint
            val assessment = GgufRamPolicy.assess(size, totalMemBytes)
            if (assessment.fit == RamFit.REFUSE) {
                destination.delete()
                error(assessment.detail ?: "Model is too large for this device")
            }
            val fromFile = GgufMetadata.contextLength(destination)?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
            val record = GgufModelRecord(
                id = id,
                displayName = displayName.ifBlank { "GGUF model" },
                fileName = safeName,
                sizeBytes = size,
                contextSize = GgufRamPolicy.clampContext(fromFile ?: assessment.recommendedContext, totalMemBytes),
                sourceLabel = sourceLabel,
                createdAtEpochMs = System.currentTimeMillis()
            )
            mutex.withLock {
                val current = readIndex().models
                writeIndex(GgufIndex(current + record))
            }
            record
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    suspend fun updateParameters(id: String, contextSize: Int, temperature: Float, totalMemBytes: Long): GgufModelRecord? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readIndex().models
            val existing = current.firstOrNull { it.id == id } ?: return@withLock null
            val updated = existing.copy(
                contextSize = GgufRamPolicy.clampContext(contextSize, totalMemBytes),
                temperature = temperature.coerceIn(0f, 2f)
            )
            writeIndex(GgufIndex(current.map { if (it.id == id) updated else it }))
            updated
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = readIndex().models
            val existing = current.firstOrNull { it.id == id } ?: return@withLock
            fileFor(existing).delete()
            writeIndex(GgufIndex(current.filterNot { it.id == id }))
        }
    }

    private fun filesDir(): File = File(root, "files")

    private fun readIndex(): GgufIndex {
        val file = File(root, "index.json")
        if (!file.isFile) return GgufIndex()
        return runCatching { json.decodeFromString<GgufIndex>(file.readText()) }.getOrDefault(GgufIndex())
    }

    private fun writeIndex(index: GgufIndex) {
        root.mkdirs()
        File(root, "index.json").writeText(json.encodeToString(index))
    }
}
