package dev.chungjungsoo.gptmobile.data.project

import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatListRowKeyTest {
    @Test
    fun keysStayStableForHeadersAndSavedChats() {
        val header = ChatListRow.Header(key = "project-4", title = "Work", projectId = 4)
        val chat = ChatListRow.Chat(
            chat = ChatRoomV2(id = 9, title = "Benchmark long chat", enabledPlatform = listOf("openai")),
            chatIndex = 2
        )

        assertEquals("header-project-4", chatListRowKey(header))
        assertEquals("chat-9", chatListRowKey(chat))
    }

    @Test
    fun unsavedChatsDoNotShareAKey() {
        val first = ChatListRow.Chat(
            chat = ChatRoomV2(id = 0, title = "New", enabledPlatform = emptyList(), createdAt = 10),
            chatIndex = 0
        )
        val second = ChatListRow.Chat(
            chat = ChatRoomV2(id = 0, title = "New", enabledPlatform = emptyList(), createdAt = 11),
            chatIndex = 1
        )

        assertEquals("chat-new-10-0", chatListRowKey(first))
        assertEquals("chat-new-11-1", chatListRowKey(second))
    }
}
