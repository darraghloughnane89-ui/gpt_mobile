package dev.chungjungsoo.gptmobile.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatRoomRouteTest {
    @Test
    fun chatRoomRoute_includesTheVoiceFlag() {
        assertEquals(
            "chat_room/0?enabled=platform-a,platform-b&projectId=-1&voice=true",
            chatRoomRoute(0, listOf("platform-a", "platform-b"), openVoice = true)
        )
        assertEquals(
            "chat_room/4?enabled=platform-a&projectId=-1&voice=false",
            chatRoomRoute(4, listOf("platform-a"))
        )
        assertEquals(
            "chat_room/4?enabled=platform-a&projectId=9&voice=false",
            chatRoomRoute(4, listOf("platform-a"), projectId = 9)
        )
    }
}
