package dev.chungjungsoo.gptmobile.data.hermes

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

interface HermesThreadStore {
    val snapshot: StateFlow<HermesThreadSnapshot>

    suspend fun update(block: (HermesThreadSnapshot) -> HermesThreadSnapshot)
}

class FileHermesThreadStore(
    file: File
) : HermesThreadStore {
    constructor(directory: File, fileName: String) : this(File(directory, fileName))

    private val file: File = file
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    private val state = MutableStateFlow(readOrEmpty())

    override val snapshot: StateFlow<HermesThreadSnapshot> = state.asStateFlow()

    override suspend fun update(block: (HermesThreadSnapshot) -> HermesThreadSnapshot) {
        mutex.withLock {
            val next = block(state.value)
            write(next)
            state.value = next
        }
    }

    private fun readOrEmpty(): HermesThreadSnapshot {
        if (!file.isFile) return HermesThreadSnapshot()
        return try {
            json.decodeFromString(HermesThreadSnapshot.serializer(), file.readText())
        } catch (_: Exception) {
            HermesThreadSnapshot()
        }
    }

    private fun write(snapshot: HermesThreadSnapshot) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.encodeToString(HermesThreadSnapshot.serializer(), snapshot))
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }
}
