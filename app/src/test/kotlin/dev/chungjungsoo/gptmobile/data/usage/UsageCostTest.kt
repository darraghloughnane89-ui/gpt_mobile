package dev.chungjungsoo.gptmobile.data.usage

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageCostTest {
    @Test
    fun costMicros_knownPrice_roundsDownToMicros() {
        val cost = UsageCost.costMicros(
            inputTokens = 1_000_000,
            outputTokens = 500_000,
            inputPerMillion = 2_500_000,
            outputPerMillion = 10_000_000
        )
        assertEquals(2_500_000L + 5_000_000L, cost)
    }

    @Test
    fun costMicros_partialMillion_usesIntegerDivision() {
        assertEquals(1_250L, UsageCost.costMicros(1_000, 0, 1_250_000, 0))
    }

    @Test
    fun usdPerMillionToMicros_rejectsBlankAndNegative() {
        assertEquals(2_500_000L, UsageCost.usdPerMillionToMicros("2.50"))
        assertNull(UsageCost.usdPerMillionToMicros(""))
        assertNull(UsageCost.usdPerMillionToMicros("-1"))
    }

    @Test
    fun resolve_matchesLeafAfterProviderPrefix() {
        val prices = listOf(ModelPrice("gpt-4o", 2_500_000, 10_000_000, 0))
        val resolved = UsageCost.resolve("openai/gpt-4o", prices)
        assertEquals("gpt-4o", resolved?.modelKey)
    }

    @Test
    fun summarize_groupsModelDayAndMonthWithCurrentPrices() {
        val zone = ZoneOffset.UTC
        val price = ModelPrice("gpt-4o", 1_000_000, 2_000_000, 0)
        val events = listOf(
            event("gpt-4o", 1_000, 500, epochSeconds(2026, 3, 2)),
            event("gpt-4o", 1_000, 500, epochSeconds(2026, 3, 2)),
            event("other", 10, 10, epochSeconds(2026, 4, 1))
        )
        val summary = UsageSummaryCalculator.summarize(events, listOf(price), zone)

        assertEquals(2_010L, summary.totalInputTokens)
        val march = summary.byMonth.first { it.label == "2026-03" }
        assertEquals(2_000L, march.inputTokens)
        assertEquals(1_000L, march.outputTokens)
        assertEquals(UsageCost.costMicros(2_000, 1_000, 1_000_000, 2_000_000), march.costMicros)
        assertEquals(2, summary.byDay.size)
        assertEquals("gpt-4o", summary.byModel.first().model)
    }

    private fun event(model: String, input: Int, output: Int, createdAt: Long) = UsageEvent(
        platformUid = "platform",
        model = model,
        chatId = 1,
        inputTokens = input,
        outputTokens = output,
        estimated = false,
        costMicros = 0,
        createdAt = createdAt
    )

    private fun epochSeconds(year: Int, month: Int, day: Int): Long =
        java.time.LocalDate.of(year, month, day).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
}
