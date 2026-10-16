package dev.chungjungsoo.gptmobile.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatLibraryCodecTest {
    @Test
    fun encodeDecode_roundTripsLibraryState() {
        val state = ChatLibraryState(
            pinnedChatIds = setOf(2, 8),
            archivedChatIds = setOf(4),
            temporaryChatIds = setOf(9),
            folders = listOf(ChatFolder("work", "Work")),
            chatFolderIds = mapOf(2 to "work")
        )

        assertEquals(state, ChatLibraryCodec.decode(ChatLibraryCodec.encode(state)))
    }

    @Test
    fun decode_blankOrCorruptJson_returnsEmptyLibrary() {
        assertEquals(ChatLibraryState(), ChatLibraryCodec.decode(null))
        assertEquals(ChatLibraryState(), ChatLibraryCodec.decode("   "))
        assertEquals(ChatLibraryState(), ChatLibraryCodec.decode("{not json"))
    }
}
