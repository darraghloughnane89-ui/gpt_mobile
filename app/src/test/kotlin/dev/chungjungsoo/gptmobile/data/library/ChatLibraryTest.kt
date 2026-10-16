package dev.chungjungsoo.gptmobile.data.library

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLibraryTest {
    private data class Chat(val id: Int, val updatedAt: Long)

    @Test
    fun pinRestoresArchivedChatAndArchiveClearsPin() {
        val pinned = ChatLibraryState().archive(4).pin(4)

        assertTrue(4 in pinned.pinnedChatIds)
        assertTrue(4 !in pinned.archivedChatIds)
        val archived = pinned.archive(4)
        assertTrue(4 in archived.archivedChatIds)
        assertTrue(4 !in archived.pinnedChatIds)
    }

    @Test
    fun inboxHidesArchivedFolderedAndTemporaryChats() {
        val chats = listOf(
            Chat(1, 10),
            Chat(2, 30),
            Chat(3, 20),
            Chat(4, 40),
            Chat(5, 50)
        )
        val library = ChatLibraryState()
            .pin(2)
            .archive(3)
            .markTemporary(4)
            .addFolder("work", "Work").state
            .moveToFolder(5, "work")

        val inbox = projectChatList(chats, Chat::id, Chat::updatedAt, library, ChatListFilter.Inbox, searching = false)

        assertEquals(listOf(2, 1), inbox.map { it.id })
    }

    @Test
    fun pinnedFilterKeepsFolderedChatsAndSearchKeepsArchivedChats() {
        val chats = listOf(Chat(1, 10), Chat(2, 20), Chat(3, 30))
        val library = ChatLibraryState()
            .pin(1)
            .archive(2)
            .markTemporary(3)
            .addFolder("work", "Work").state
            .moveToFolder(1, "work")

        val pinned = projectChatList(chats, Chat::id, Chat::updatedAt, library, ChatListFilter.Pinned, searching = false)
        val search = projectChatList(chats, Chat::id, Chat::updatedAt, library, ChatListFilter.Inbox, searching = true)
        val folder = projectChatList(chats, Chat::id, Chat::updatedAt, library, ChatListFilter.Folder("work"), searching = false)

        assertEquals(listOf(1), pinned.map { it.id })
        assertEquals(listOf(1, 2), search.map { it.id })
        assertEquals(listOf(1), folder.map { it.id })
    }

    @Test
    fun folderNamesAreNormalizedAndRejectedWhenDuplicated() {
        val created = ChatLibraryState().addFolder("id", "  Site   notes  ")

        assertNull(created.issue)
        assertEquals("Site notes", created.state.folders.single().name)
        assertEquals(FolderNameIssue.Duplicate, created.state.addFolder("other", "site notes").issue)
        assertEquals(FolderNameIssue.Blank, folderNameIssue(emptyList(), "   "))
        assertEquals(FolderNameIssue.TooLong, folderNameIssue(emptyList(), "a".repeat(MAX_FOLDER_NAME_LENGTH + 1)))
    }

    @Test
    fun deleteFolderReturnsChatsToInboxAndForgetDropsEveryMark() {
        val library = ChatLibraryState()
            .pin(7)
            .addFolder("id", "Work").state
            .moveToFolder(7, "id")
            .deleteFolder("id")
            .forget(7)

        assertTrue(library.folders.isEmpty())
        assertTrue(library.chatFolderIds.isEmpty())
        assertTrue(library.pinnedChatIds.isEmpty())
    }

    @Test
    fun memoryStoreUpdateIsVisible() = runBlocking {
        val store = MemoryChatLibraryStore()

        val updated = store.update { it.pin(9).markTemporary(3) }

        assertEquals(updated, store.current())
        assertTrue(9 in updated.pinnedChatIds)
        assertTrue(3 in updated.temporaryChatIds)
    }
}
