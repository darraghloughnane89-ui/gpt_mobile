package dev.chungjungsoo.gptmobile.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5_000)
        }
    }

    @Test
    fun chatScrollAndStreaming() {
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false
        ) {
            startActivityAndWait(scenarioIntent(SCENARIO_SCROLL))
            val list = device.wait(Until.findObject(By.res(TARGET_PACKAGE, MESSAGE_LIST_TAG)), 15_000)
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.UP)
            device.waitForIdle()
            startActivityAndWait(scenarioIntent(SCENARIO_STREAM))
            device.wait(Until.findObject(By.res(TARGET_PACKAGE, MESSAGE_LIST_TAG)), 15_000)
            Thread.sleep(2_000)
        }
    }
}
