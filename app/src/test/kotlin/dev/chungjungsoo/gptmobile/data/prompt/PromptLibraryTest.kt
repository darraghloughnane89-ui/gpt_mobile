package dev.chungjungsoo.gptmobile.data.prompt

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptLibraryTest {
    @Test
    fun placeholders_areUniqueAndFilled() {
        val body = "Explain {topic} to {audience}. Again: {topic}."
        assertEquals(listOf("topic", "audience"), PromptPlaceholders.names(body))
        assertEquals(
            "Explain gravity to kids. Again: gravity.",
            PromptPlaceholders.fill(body, mapOf("topic" to "gravity", "audience" to "kids"))
        )
    }

    @Test
    fun placeholders_ignoreMalformedTokens() {
        assertEquals(emptyList<String>(), PromptPlaceholders.names("no braces { } {1topic} {topic-name}"))
        assertEquals(listOf("A"), PromptPlaceholders.names("{A}"))
    }

    @Test
    fun codec_roundTripsDocumentAndBareArray() {
        val prompts = listOf(
            SavedPrompt(id = "one", title = "Summarize", body = "Summarize {topic}", favourite = true, createdAt = 1, updatedAt = 2)
        )
        val encoded = PromptLibraryCodec.encode(prompts)
        val decoded = PromptLibraryCodec.decode(encoded)
        assertEquals(prompts, decoded?.prompts)

        val array = PromptLibraryCodec.decode(
            """[{"id":"two","title":"Translate","body":"Translate {text}","favourite":false}]"""
        )
        assertEquals("Translate", array?.prompts?.single()?.title)
        assertNull(PromptLibraryCodec.decode("{not json"))
        assertEquals(0, PromptLibraryCodec.decode("")?.prompts?.size)
    }

    @Test
    fun codec_rejectsFutureVersion() {
        val document = PromptLibraryCodec.decode("""{"version":9,"prompts":[]}""")
        assertEquals(9, document?.version)
    }

    @Test
    fun repository_savesFavouritesAndMergesImport() = runBlocking {
        val storage = MemoryPromptStorage()
        var now = 10L
        val repository = PromptLibraryRepositoryImpl(storage, clock = { now }, newId = { "generated" })

        val saved = repository.save(SavedPrompt(id = "", title = "  Draft  ", body = "Hello {name}"))
        assertEquals("generated", saved.id)
        assertEquals("Draft", saved.title)
        assertTrue(saved.favourite.not())

        now = 20L
        repository.setFavourite(saved.id, true)
        val listed = repository.observe().first()
        assertEquals(listOf("generated"), listed.map { it.id })
        assertTrue(listed.single().favourite)

        now = 30L
        val outcome = repository.importJson(
            """
            {
              "version": 1,
              "prompts": [
                {"id":"generated","title":"Draft","body":"Updated {name}","favourite":false,"createdAt":10},
                {"id":"other","title":"Review","body":"Review {pr}","favourite":true},
                {"id":"","title":"Missing","body":"skip"},
                {"id":"bad id","title":"Bad","body":"skip"}
              ]
            }
            """.trimIndent()
        )
        val success = outcome as PromptImportOutcome.Success
        assertEquals(1, success.counts.added)
        assertEquals(1, success.counts.updated)
        assertEquals(2, success.counts.skipped)
        val afterImport = repository.observe().first()
        assertEquals(listOf("Review", "Draft"), afterImport.map { it.title })
        assertEquals("Updated {name}", afterImport.first { it.id == "generated" }.body)
        assertEquals(10L, afterImport.first { it.id == "generated" }.createdAt)

        repository.delete("other")
        assertEquals(listOf("generated"), repository.observe().first().map { it.id })
        assertTrue(repository.exportJson().contains("Updated {name}"))
    }

    @Test
    fun repository_keepsCorruptFileUntilTheNextSave() = runBlocking {
        val storage = MemoryPromptStorage().apply { text = "not-json" }
        val repository = PromptLibraryRepositoryImpl(storage, newId = { "id" })
        assertTrue(repository.observe().first().isEmpty())
        assertEquals("not-json", storage.text)
        repository.save(SavedPrompt(id = "id", title = "Keep", body = "Body"))
        assertFalse(storage.text!!.contains("not-json"))
    }

    @Test
    fun repository_rejectsUnreadableAndFutureImports() = runBlocking {
        val repository = PromptLibraryRepositoryImpl(MemoryPromptStorage())
        assertEquals(PromptImportOutcome.Unreadable, repository.importJson("nope"))
        assertEquals(PromptImportOutcome.Unreadable, repository.importJson("""{"version":4,"prompts":[]}"""))
    }

    @Test
    fun fileStorage_roundTrips() = runBlocking {
        val directory = File(System.getProperty("java.io.tmpdir"), "prompt-library-${System.nanoTime()}")
        val storage = FilePromptLibraryStorage(File(directory, "prompt_library.json"))
        assertNull(storage.read())
        storage.write("{\"version\":1,\"prompts\":[]}")
        assertEquals("{\"version\":1,\"prompts\":[]}", storage.read())
        assertTrue(directory.deleteRecursively())
    }
}

private class MemoryPromptStorage : PromptLibraryStorage {
    var text: String? = null
    override suspend fun read(): String? = text
    override suspend fun write(contents: String) {
        text = contents
    }
}
