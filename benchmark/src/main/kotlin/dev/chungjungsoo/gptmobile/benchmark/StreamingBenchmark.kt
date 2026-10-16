package dev.chungjungsoo.gptmobile.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamingBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun streamReply() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = 3,
            setupBlock = {
                pressHome()
            }
        ) {
            startActivityAndWait(scenarioIntent(SCENARIO_STREAM))
            device.wait(Until.findObject(By.res(TARGET_PACKAGE, MESSAGE_LIST_TAG)), LIST_TIMEOUT_MS)
            Thread.sleep(STREAM_WINDOW_MS)
        }
    }

    private companion object {
        const val LIST_TIMEOUT_MS = 15_000L
        const val STREAM_WINDOW_MS = 4_000L
    }
}
