package dev.chungjungsoo.gptmobile.presentation.phonecontrol

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.chungjungsoo.gptmobile.data.phonecontrol.AccessibilityTreeReader
import dev.chungjungsoo.gptmobile.data.phonecontrol.PhoneObservationBuilder
import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.PhoneActionKind
import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.PlannedAction
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.SafetyContext
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.SafetyDecision
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.SafetyGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneControlDemoInstrumentedTest {
    @Test
    fun demoActivity_redactsPasswordAndRequiresConfirmationForSend() {
        ActivityScenario.launch(PhoneControlDemoActivity::class.java).use {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val root = waitForRoot(automation)
            assertNotNull(root)
            val node = AccessibilityTreeReader.read(root)
            root?.recycle()
            val observation = PhoneObservationBuilder.build(
                root = node,
                packageName = "dev.chungjungsoo.gptmobile",
                appLabel = "GPTMobile",
                secureWindow = false,
                screenWidth = 1080,
                screenHeight = 2400,
                visionRequested = true
            )
            assertTrue(observation.treeText.contains("Send"))
            assertFalse(observation.treeText.contains("Ignore previous instructions"))
            assertTrue(observation.passwordFieldVisible)
            assertFalse(observation.screenshotAttached)
            val decision = SafetyGate().evaluate(
                PlannedAction(PhoneActionKind.TAP, targetText = "Send"),
                SafetyContext(
                    foregroundPackage = "dev.chungjungsoo.gptmobile",
                    allowlist = setOf("dev.chungjungsoo.gptmobile")
                )
            )
            assertTrue(decision.decision == SafetyDecision.NEEDS_CONFIRMATION)
        }
    }

    private fun waitForRoot(automation: android.app.UiAutomation): AccessibilityNodeInfo? {
        val deadline = System.currentTimeMillis() + 5_000
        var root: AccessibilityNodeInfo? = null
        while (System.currentTimeMillis() < deadline && root == null) {
            automation.waitForIdle(300, 800)
            root = automation.rootInActiveWindow
        }
        return root
    }
}
