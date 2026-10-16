package dev.chungjungsoo.gptmobile.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupColdWithBaselineProfile() = startup(CompilationMode.Partial(), StartupMode.COLD)

    @Test
    fun startupColdWithoutBaselineProfile() = startup(CompilationMode.None(), StartupMode.COLD)

    @Test
    fun startupWarmWithBaselineProfile() = startup(CompilationMode.Partial(), StartupMode.WARM)

    private fun startup(compilationMode: CompilationMode, startupMode: StartupMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = startupMode,
            iterations = 5
        ) {
            pressHome()
            startActivityAndWait()
        }
    }
}
