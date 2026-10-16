package dev.chungjungsoo.gptmobile.presentation.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BenchmarkScenarioTest {
    @Test
    fun `benchmark scenarios are recognized only when the harness is enabled`() {
        assertEquals(
            BenchmarkChatSeeder.SCENARIO_SCROLL,
            parseBenchmarkScenario(
                enabled = true,
                action = BenchmarkChatSeeder.ACTION,
                scenario = BenchmarkChatSeeder.SCENARIO_SCROLL
            )
        )
        assertEquals(
            BenchmarkChatSeeder.SCENARIO_STREAM,
            parseBenchmarkScenario(
                enabled = true,
                action = BenchmarkChatSeeder.ACTION,
                scenario = BenchmarkChatSeeder.SCENARIO_STREAM
            )
        )
        assertNull(
            parseBenchmarkScenario(
                enabled = false,
                action = BenchmarkChatSeeder.ACTION,
                scenario = BenchmarkChatSeeder.SCENARIO_SCROLL
            )
        )
        assertNull(
            parseBenchmarkScenario(
                enabled = true,
                action = "android.intent.action.MAIN",
                scenario = BenchmarkChatSeeder.SCENARIO_SCROLL
            )
        )
        assertNull(
            parseBenchmarkScenario(
                enabled = true,
                action = BenchmarkChatSeeder.ACTION,
                scenario = "unknown"
            )
        )
    }
}
