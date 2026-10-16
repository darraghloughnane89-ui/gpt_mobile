package dev.chungjungsoo.gptmobile.data.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedChatsTest {
    private val chats = listOf(
        WidgetChat(1, "Alpha", listOf("p")),
        WidgetChat(2, "Beta", listOf("p")),
        WidgetChat(3, "Gamma", listOf("p"))
    )

    @Test
    fun toggle_pinsNewestFirstAndSkipsUnsavedChats() {
        val pinned = PinnedChats.toggle(listOf(1), listOf(3, 0, 2), pin = true)

        assertEquals(listOf(2, 3, 1), pinned)
    }

    @Test
    fun toggle_removesOnlyTheRequestedChats() {
        assertEquals(listOf(1), PinnedChats.toggle(listOf(2, 1), listOf(2), pin = false))
    }

    @Test
    fun select_keepsPinOrderAndDropsMissingChats() {
        val selected = PinnedChats.select(chats, pinnedIds = listOf(3, 9, 1), limit = 2)

        assertEquals(listOf(3, 1), selected.map { it.id })
    }
}
