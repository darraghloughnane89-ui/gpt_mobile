package dev.chungjungsoo.gptmobile.data.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineSummaryTest {
    @Test
    fun fromAssistantTexts_usesTheLatestReplyAndDropsSecrets() {
        val summary = RoutineSummary.fromAssistantTexts(
            listOf("older", "Weather is clear. api_key=sk-live-should-not-show")
        )

        assertEquals("Weather is clear. ${WidgetSecrets.REDACTED}", summary)
    }

    @Test
    fun fromAssistantTexts_truncatesLongBriefings() {
        val summary = RoutineSummary.fromAssistantTexts(listOf("a".repeat(400)), maxChars = 20)

        assertEquals(20, summary?.length)
        assertTrue(summary!!.endsWith("…"))
    }

    @Test
    fun fromAssistantTexts_ignoresBlankReplies() {
        assertNull(RoutineSummary.fromAssistantTexts(listOf("  ", "")))
    }

    @Test
    fun routineIdFor_morningBriefingUsesStableId() {
        assertEquals(RoutineSummary.MORNING_BRIEFING_ID, RoutineSummary.routineIdFor("Morning briefing", 4))
        assertEquals("chat-4", RoutineSummary.routineIdFor("Site notes", 4))
    }
}
