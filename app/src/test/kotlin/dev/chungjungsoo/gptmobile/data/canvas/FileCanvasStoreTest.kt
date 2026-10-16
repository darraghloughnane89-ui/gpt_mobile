package dev.chungjungsoo.gptmobile.data.canvas

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileCanvasStoreTest {
    @Test
    fun saveAndLoad_roundTripsTheLibrary() = runBlocking {
        val directory = tempDirectory()
        val store = FileCanvasStore(directory)
        val library = CanvasLibrary(
            documents = listOf(
                CanvasDocument(
                    id = "doc",
                    chatRoomId = 3,
                    title = "Notes",
                    kind = CanvasKind.Markdown,
                    language = "markdown",
                    updatedAt = 10,
                    versions = listOf(
                        CanvasVersion(
                            id = "v1",
                            content = "Hello \"canvas\"",
                            title = "Notes",
                            createdAt = 10,
                            source = CanvasVersionSource.Open
                        )
                    )
                )
            ),
            appliedFingerprints = listOf("fp")
        )

        store.save(library)
        val loaded = FileCanvasStore(directory).load()

        assertEquals(library, loaded)
    }

    @Test
    fun load_corruptFile_returnsAnEmptyLibrary() = runBlocking {
        val directory = tempDirectory()
        File(directory, "library.json").writeText("{not json")

        assertTrue(FileCanvasStore(directory).load().documents.isEmpty())
    }

    private fun tempDirectory(): File {
        val directory = File(System.getProperty("java.io.tmpdir"), "canvas-store-${System.nanoTime()}")
        directory.mkdirs()
        return directory
    }
}
