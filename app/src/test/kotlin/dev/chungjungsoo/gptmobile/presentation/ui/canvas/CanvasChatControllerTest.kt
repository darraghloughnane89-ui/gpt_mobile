package dev.chungjungsoo.gptmobile.presentation.ui.canvas

import dev.chungjungsoo.gptmobile.data.canvas.CanvasRepository
import dev.chungjungsoo.gptmobile.data.canvas.FileCanvasStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanvasChatControllerTest {
    @Test
    fun onAssistantReplies_opensTheLatestDirective() = runBlocking {
        val controller = controller()
        controller.bind(8)
        controller.refreshRoom()

        controller.onAssistantReplies(
            olderMessages = emptyList(),
            latestMessages = listOf(
                """
                ```robin-canvas
                title: Trip notes
                kind: markdown
                action: open
                ---
                # Day 1
                ```
                """.trimIndent()
            )
        )

        assertEquals("Trip notes", controller.document.value?.title)
        assertEquals("# Day 1", controller.document.value?.content)
        assertEquals(false, controller.chatOpen.value)
    }

    @Test
    fun acceptAndReject_followTheProposal() = runBlocking {
        val controller = controller()
        controller.bind(8)
        controller.refreshRoom()
        val created = controller.createBlank("Draft")
        controller.onAssistantReplies(
            olderMessages = emptyList(),
            latestMessages = listOf(
                """
                ```robin-canvas
                id: ${created.id}
                action: propose
                ---
                updated
                ```
                """.trimIndent()
            )
        )

        assertEquals("", controller.document.value?.content)
        assertEquals("updated", controller.document.value?.proposal?.proposedContent)
        controller.rejectProposal()
        assertNull(controller.document.value?.proposal)
        assertEquals("", controller.document.value?.content)

        controller.onAssistantReplies(
            olderMessages = emptyList(),
            latestMessages = listOf(
                """
                ```robin-canvas
                id: ${created.id}
                action: propose
                ---
                accepted text
                ```
                """.trimIndent()
            )
        )
        controller.acceptProposal()

        assertEquals("accepted text", controller.document.value?.content)
        assertEquals(2, controller.document.value?.versions?.size)
    }

    @Test
    fun reassign_movesABlankCanvasOntoTheSavedChat() = runBlocking {
        val repository = repository()
        val controller = CanvasChatController(repository)
        controller.bind(0)
        controller.createBlank("Untitled canvas")
        controller.bind(15)
        controller.refreshRoom()

        assertEquals(15, controller.document.value?.chatRoomId)
        assertEquals(1, repository.documents(15).size)
        assertEquals(0, repository.documents(0).size)
    }

    private fun controller(): CanvasChatController = CanvasChatController(repository())

    private fun repository(): CanvasRepository {
        val directory = File(System.getProperty("java.io.tmpdir"), "canvas-controller-${System.nanoTime()}")
        directory.mkdirs()
        return CanvasRepository(FileCanvasStore(directory))
    }
}
