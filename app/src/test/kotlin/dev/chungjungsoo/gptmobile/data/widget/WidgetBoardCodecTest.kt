package dev.chungjungsoo.gptmobile.data.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetBoardCodecTest {
    @Test
    fun pinnedIds_roundTripAndSkipGarbage() {
        assertEquals(listOf(4, 9), WidgetBoardCodec.decodePinned("4, no, 9,4"))
        assertEquals("4,9", WidgetBoardCodec.encodePinned(listOf(4, 9, 4)))
        assertEquals(emptyList<Int>(), WidgetBoardCodec.decodePinned(null))
    }

    @Test
    fun routine_roundTripAndRejectsCorruptJson() {
        val result = RoutineResult(
            routineId = "morning-briefing",
            name = "Morning briefing",
            summary = "Clear roads",
            chatId = 3,
            enabledPlatforms = listOf("a", "b"),
            completedAtEpochSeconds = 42
        )

        assertEquals(result, WidgetBoardCodec.decodeRoutine(WidgetBoardCodec.encodeRoutine(result)))
        assertNull(WidgetBoardCodec.decodeRoutine("{"))
        assertNull(WidgetBoardCodec.decodeRoutine(null))
    }
}
