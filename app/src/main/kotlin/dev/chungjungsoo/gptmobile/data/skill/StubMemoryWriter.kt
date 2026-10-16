package dev.chungjungsoo.gptmobile.data.skill

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder until stream (f) binds its own [MemoryWriter].
 * Facts remain on the skill proposal, so a later memory store can still read them.
 */
@Singleton
class StubMemoryWriter @Inject constructor() : MemoryWriter {
    override suspend fun write(facts: List<MemoryFact>): MemoryWriteResult {
        if (facts.isEmpty()) return MemoryWriteResult.Written(0)
        return MemoryWriteResult.Deferred(
            "Memory storage is not installed yet. These facts stay on the skill proposal."
        )
    }
}
