package dev.chungjungsoo.gptmobile.data.skill

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
