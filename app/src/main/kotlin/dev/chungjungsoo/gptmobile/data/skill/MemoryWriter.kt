package dev.chungjungsoo.gptmobile.data.skill

/**
 * Declarative facts proposed by the learning loop.
 * Stream (f) implements this. Skills stay procedures; facts stay data.
 * [projectId] is reserved for stream (a) and is unused until projects exist.
 */
data class MemoryFact(
    val text: String,
    val source: String,
    val chatId: Int? = null,
    val projectId: Long? = null
)

sealed class MemoryWriteResult {
    data class Written(val count: Int) : MemoryWriteResult()
    data class Deferred(val reason: String) : MemoryWriteResult()
}

interface MemoryWriter {
    suspend fun write(facts: List<MemoryFact>): MemoryWriteResult
}
