package dev.chungjungsoo.gptmobile.data.memory

import dev.chungjungsoo.gptmobile.data.skill.MemoryFact
import dev.chungjungsoo.gptmobile.data.skill.MemoryWriteResult
import dev.chungjungsoo.gptmobile.data.skill.MemoryWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists approved skill memories. Facts stay global unless [MemoryFact.projectId] is set.
 * Skills code that still binds a stub writer must drop that binding when the branches merge.
 */
@Singleton
class RoomMemoryWriter @Inject constructor(
    private val memoryRepository: MemoryRepository
) : MemoryWriter {
    override suspend fun write(facts: List<MemoryFact>): MemoryWriteResult {
        if (facts.isEmpty()) return MemoryWriteResult.Written(0)
        val accepted = facts.filter { it.text.isNotBlank() }
        if (accepted.isEmpty()) {
            return MemoryWriteResult.Deferred("No memory facts to store.")
        }
        var count = 0
        for (fact in accepted) {
            val saved = memoryRepository.saveFact(
                text = fact.text,
                source = fact.source,
                projectId = fact.projectId
            )
            if (saved != null) count++
        }
        return MemoryWriteResult.Written(count)
    }
}
