package dev.chungjungsoo.gptmobile.data.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPresentationTest {
    private val secret = "sk-proj-widgetsecretvalue"
    private val routine = RoutineResult(
        routineId = RoutineSummary.MORNING_BRIEFING_ID,
        name = "Morning briefing",
        summary = "Traffic is light. $secret",
        chatId = 7,
        enabledPlatforms = listOf("platform-a"),
        completedAtEpochSeconds = 10
    )
    private val chat = WidgetChat(7, "Morning briefing $secret", listOf("platform-a"))

    @Test
    fun routine_lockedWithoutOptIn_containsNoChatContent() {
        val state = WidgetPresentation.routine(
            deviceLocked = true,
            onKeyguard = false,
            allowWhenLocked = false,
            result = routine,
            liveChat = chat,
            liveSummary = "Bring the $secret key"
        )

        assertEquals(RoutineWidgetState.Locked, state)
        assertFalse(state.toString().contains(secret))
    }

    @Test
    fun routine_lockedWithOptIn_redactsSecrets() {
        val state = WidgetPresentation.routine(
            deviceLocked = true,
            onKeyguard = true,
            allowWhenLocked = true,
            result = routine,
            liveChat = chat,
            liveSummary = "Bring the $secret key"
        ) as RoutineWidgetState.Ready

        assertFalse(state.summary.contains(secret))
        assertFalse(state.name.contains(secret))
        assertTrue(state.summary.contains("Bring the"))
        assertEquals(7, state.chat.id)
    }

    @Test
    fun routine_missingChat_staysEmpty() {
        val state = WidgetPresentation.routine(
            deviceLocked = false,
            onKeyguard = false,
            allowWhenLocked = false,
            result = routine,
            liveChat = null,
            liveSummary = "still here"
        )

        assertEquals(RoutineWidgetState.Empty, state)
    }

    @Test
    fun pinned_lockedWithoutOptIn_hidesTitles() {
        val state = WidgetPresentation.pinned(
            deviceLocked = false,
            onKeyguard = true,
            allowWhenLocked = false,
            chats = listOf(chat),
            pinnedIds = listOf(7),
            limit = 4
        )

        assertEquals(PinnedWidgetState.Locked, state)
        assertFalse(state.toString().contains("Morning"))
        assertFalse(state.toString().contains(secret))
    }

    @Test
    fun pinned_unlocked_keepsOrderAndRedactsTitles() {
        val state = WidgetPresentation.pinned(
            deviceLocked = false,
            onKeyguard = false,
            allowWhenLocked = false,
            chats = listOf(chat, WidgetChat(8, "Grocery list", listOf("platform-a"))),
            pinnedIds = listOf(8, 7),
            limit = 4
        ) as PinnedWidgetState.Ready

        assertEquals(listOf(8, 7), state.chats.map { it.id })
        assertFalse(state.chats[1].title.contains(secret))
    }
}
