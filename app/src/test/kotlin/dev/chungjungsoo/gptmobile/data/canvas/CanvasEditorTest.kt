package dev.chungjungsoo.gptmobile.data.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasEditorTest {
    private val ids = IdSource()

    @Test
    fun ingest_latestOpen_createsDocumentAndSecondPassDoesNotDuplicate() {
        val message = openFence(title = "Trip notes", body = "# Day 1")
        val first = ingestCanvasMessages(
            library = CanvasLibrary(),
            chatRoomId = 7,
            olderMessages = emptyList(),
            latestMessages = listOf(message),
            now = 1_000,
            newId = ids::next
        )
        val again = ingestCanvasMessages(
            library = first.first,
            chatRoomId = 7,
            olderMessages = emptyList(),
            latestMessages = listOf(message),
            now = 2_000,
            newId = ids::next
        )

        assertEquals(1, first.first.documents.size)
        assertEquals("Trip notes", first.first.documents.single().title)
        assertEquals("# Day 1", first.first.documents.single().content)
        assertEquals(first.first.documents.single().id, first.second)
        assertNull(again.second)
        assertEquals(1, again.first.documents.size)
    }

    @Test
    fun ingest_olderMessage_doesNotCreateADocument() {
        val library = ingestCanvasMessages(
            library = CanvasLibrary(),
            chatRoomId = 7,
            olderMessages = listOf(openFence(title = "Old", body = "past")),
            latestMessages = listOf("No canvas here."),
            now = 1_000,
            newId = ids::next
        ).first

        assertTrue(library.documents.isEmpty())
        assertEquals(1, library.appliedFingerprints.size)
    }

    @Test
    fun ingest_propose_waitsForAcceptAndRejectLeavesTheOriginal() {
        val opened = ingestCanvasMessages(
            CanvasLibrary(),
            chatRoomId = 4,
            olderMessages = emptyList(),
            latestMessages = listOf(openFence(title = "Notes", body = "one")),
            now = 1_000,
            newId = ids::next
        ).first
        val document = opened.documents.single()
        val proposed = ingestCanvasMessages(
            opened,
            chatRoomId = 4,
            olderMessages = emptyList(),
            latestMessages = listOf(proposeFence(document.id, "two")),
            now = 2_000,
            newId = ids::next
        ).first
        val withProposal = proposed.documents.single()

        assertEquals("one", withProposal.content)
        assertEquals("two", withProposal.proposal?.proposedContent)

        val accepted = acceptCanvasProposal(proposed, document.id, now = 3_000, newId = ids::next)
        assertEquals("two", accepted.documents.single().content)
        assertEquals(CanvasVersionSource.Model, accepted.documents.single().versions.last().source)
        assertNull(accepted.documents.single().proposal)

        val reopened = ingestCanvasMessages(
            opened,
            chatRoomId = 4,
            olderMessages = emptyList(),
            latestMessages = listOf(proposeFence(document.id, "three")),
            now = 4_000,
            newId = ids::next
        ).first
        val rejected = rejectCanvasProposal(reopened, document.id)

        assertEquals("one", rejected.documents.single().content)
        assertNull(rejected.documents.single().proposal)
    }

    @Test
    fun editCanvasContent_coalescesBurstAndAppendsAfterTheWindow() {
        val created = createCanvas(
            CanvasLibrary(),
            chatRoomId = 1,
            title = "Draft",
            kind = CanvasKind.Markdown,
            language = "markdown",
            content = "a",
            source = CanvasVersionSource.Open,
            now = 1_000,
            newId = ids::next
        ).first
        val id = created.documents.single().id
        val firstEdit = editCanvasContent(created, id, "ab", now = 1_100, newId = ids::next)
        val burst = editCanvasContent(firstEdit, id, "abc", now = 1_500, newId = ids::next)
        val later = editCanvasContent(burst, id, "abcd", now = 5_000, newId = ids::next)

        assertEquals(2, burst.documents.single().versions.size)
        assertEquals("abc", burst.documents.single().content)
        assertEquals(3, later.documents.single().versions.size)
        assertEquals(CanvasVersionSource.User, later.documents.single().versions.last().source)
    }

    @Test
    fun editCanvasContent_overTheLimit_keepsThePreviousDocument() {
        val created = createCanvas(
            CanvasLibrary(),
            chatRoomId = 1,
            title = "Draft",
            kind = CanvasKind.Code,
            language = "kotlin",
            content = "fun main() {}",
            source = CanvasVersionSource.Open,
            now = 1_000,
            newId = ids::next
        ).first
        val id = created.documents.single().id
        val edited = editCanvasContent(created, id, "x".repeat(CanvasLimits.MAX_CONTENT_CHARS + 1), now = 2_000, newId = ids::next)

        assertEquals("fun main() {}", edited.documents.single().content)
    }

    @Test
    fun proposeSelection_splicesTheRememberedPassage() {
        val created = createCanvas(
            CanvasLibrary(),
            chatRoomId = 3,
            title = "Draft",
            kind = CanvasKind.Markdown,
            language = "markdown",
            content = "Keep this. Rewrite me. Keep the end.",
            source = CanvasVersionSource.Open,
            now = 1_000,
            newId = ids::next
        ).first
        val id = created.documents.single().id
        val armed = rememberCanvasSelection(
            created,
            id,
            PendingSelection(start = 11, end = 22, text = "Rewrite me.")
        )
        val proposed = ingestCanvasMessages(
            armed,
            chatRoomId = 3,
            olderMessages = emptyList(),
            latestMessages = listOf(selectionFence(id, "Done.")),
            now = 2_000,
            newId = ids::next
        ).first

        assertEquals("Keep this. Done. Keep the end.", proposed.documents.single().proposal?.proposedContent)
        assertEquals("Keep this. Rewrite me. Keep the end.", proposed.documents.single().content)
    }

    @Test
    fun restoreCanvasVersion_appendsTheOlderText() {
        val created = createCanvas(
            CanvasLibrary(),
            chatRoomId = 1,
            title = "Draft",
            kind = CanvasKind.Markdown,
            language = "markdown",
            content = "first",
            source = CanvasVersionSource.Open,
            now = 1_000,
            newId = ids::next
        ).first
        val id = created.documents.single().id
        val originalVersion = created.documents.single().versions.single().id
        val edited = editCanvasContent(created, id, "second", now = 9_000, newId = ids::next)
        val restored = restoreCanvasVersion(edited, id, originalVersion, now = 10_000, newId = ids::next)

        assertEquals("first", restored.documents.single().content)
        assertEquals(3, restored.documents.single().versions.size)
    }

    @Test
    fun reassignCanvasChat_movesOnlyTheUnsavedChat() {
        val first = createCanvas(
            CanvasLibrary(),
            chatRoomId = 0,
            title = "New",
            kind = CanvasKind.Markdown,
            language = "markdown",
            content = "",
            source = CanvasVersionSource.Open,
            now = 1_000,
            newId = ids::next
        ).first
        val both = createCanvas(
            first,
            chatRoomId = 9,
            title = "Saved",
            kind = CanvasKind.Markdown,
            language = "markdown",
            content = "stay",
            source = CanvasVersionSource.Open,
            now = 1_100,
            newId = ids::next
        ).first

        val moved = reassignCanvasChat(both, fromChatRoomId = 0, toChatRoomId = 12)

        assertEquals(setOf(12, 9), moved.documents.map { it.chatRoomId }.toSet())
    }

    @Test
    fun openCanvasFromAssistant_htmlOnly_doesNotCreateADocument() {
        val result = openCanvasFromAssistant(
            CanvasLibrary(),
            chatRoomId = 2,
            content = "```html\n<p>Hi</p>\n```",
            now = 1_000,
            newId = ids::next
        )

        assertTrue(result.second is OpenCanvasResult.PreviewOwned)
        assertTrue(result.first.documents.isEmpty())
    }

    @Test
    fun openCanvasFromAssistant_codeFence_opensThatCode() {
        val result = openCanvasFromAssistant(
            CanvasLibrary(),
            chatRoomId = 2,
            content = "```kotlin\nfun main() {}\n```",
            now = 1_000,
            newId = ids::next
        ).second as OpenCanvasResult.Opened

        assertEquals(CanvasKind.Code, result.document.kind)
        assertEquals("kotlin", result.document.language)
        assertEquals("fun main() {}", result.document.content)
    }

    private fun openFence(title: String, body: String) = """
        ```robin-canvas
        title: $title
        kind: markdown
        action: open
        ---
        $body
        ```
    """.trimIndent()

    private fun proposeFence(id: String, body: String) = """
        ```robin-canvas
        id: $id
        action: propose
        ---
        $body
        ```
    """.trimIndent()

    private fun selectionFence(id: String, body: String) = """
        ```robin-canvas
        id: $id
        action: propose-selection
        ---
        $body
        ```
    """.trimIndent()

    private class IdSource {
        private var value = 0
        fun next(): String {
            value += 1
            return "id-$value"
        }
    }
}
