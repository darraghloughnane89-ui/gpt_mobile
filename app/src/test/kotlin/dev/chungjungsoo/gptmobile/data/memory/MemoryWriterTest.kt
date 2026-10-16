package dev.chungjungsoo.gptmobile.data.memory

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.chungjungsoo.gptmobile.data.privacy.PrivacySettingsRepository
import dev.chungjungsoo.gptmobile.data.skill.MemoryFact
import dev.chungjungsoo.gptmobile.data.skill.MemoryWriteResult
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryWriterTest {
    @Test
    fun write_emptyList_writesNothing() = runBlocking {
        val harness = harness()
        val result = harness.writer.write(emptyList())
        assertEquals(MemoryWriteResult.Written(0), result)
        assertTrue(harness.dao.rows.isEmpty())
    }

    @Test
    fun write_blankFacts_areDeferred() = runBlocking {
        val harness = harness()
        val result = harness.writer.write(listOf(MemoryFact("  ", "skill-review")))
        assertTrue(result is MemoryWriteResult.Deferred)
        assertTrue(harness.dao.rows.isEmpty())
    }

    @Test
    fun write_skillFact_landsInMemoryWithSourceAndProject() = runBlocking {
        val harness = harness()
        val result = harness.writer.write(
            listOf(MemoryFact(text = "I like tea", source = "skill-review", chatId = 4, projectId = 9))
        )
        assertEquals(MemoryWriteResult.Written(1), result)
        val saved = harness.dao.rows.single()
        assertEquals("I like tea", saved.text)
        assertEquals("skill-review", saved.source)
        assertEquals(9L, saved.projectId)
        assertEquals(false, saved.paused)
    }

    @Test
    fun write_sameText_updatesInsteadOfDuplicating() = runBlocking {
        val harness = harness()
        harness.writer.write(listOf(MemoryFact("I like tea", "explicit")))
        val result = harness.writer.write(listOf(MemoryFact("I like tea", "skill-review", projectId = 3)))
        assertEquals(MemoryWriteResult.Written(1), result)
        assertEquals(1, harness.dao.rows.size)
        assertEquals("skill-review", harness.dao.rows.single().source)
        assertEquals(3L, harness.dao.rows.single().projectId)
    }

    private fun harness(): Harness {
        val directory = Files.createTempDirectory("memory-writer").toFile()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO),
            produceFile = { directory.resolve("privacy.preferences_pb") }
        )
        val dao = FakeMemoryDao()
        val writer = RoomMemoryWriter(MemoryRepository(dao, PrivacySettingsRepository(dataStore)))
        return Harness(dao, writer)
    }

    private data class Harness(val dao: FakeMemoryDao, val writer: RoomMemoryWriter)
}

private class FakeMemoryDao : MemoryDao {
    val rows = mutableListOf<Memory>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<Memory>> = flowOf(rows.toList())

    override suspend fun getActive(): List<Memory> = rows.filter { !it.paused }

    override suspend fun getAll(): List<Memory> = rows.toList()

    override suspend fun getById(id: Long): Memory? = rows.firstOrNull { it.id == id }

    override suspend fun findByText(text: String): Memory? = rows.firstOrNull { it.text.equals(text, ignoreCase = true) }

    override suspend fun insert(memory: Memory): Long {
        val id = nextId++
        rows += memory.copy(id = id)
        return id
    }

    override suspend fun update(memory: Memory) {
        val index = rows.indexOfFirst { it.id == memory.id }
        if (index >= 0) rows[index] = memory
    }

    override suspend fun deleteById(id: Long) {
        rows.removeAll { it.id == id }
    }

    override suspend fun deleteAll() {
        rows.clear()
    }

    override suspend fun memoryEnabled(chatId: Int): Boolean? = null

    override suspend fun upsertChatSetting(setting: ChatMemorySetting) = Unit
}
