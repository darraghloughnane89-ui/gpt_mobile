package dev.chungjungsoo.gptmobile.smoke.quickask

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ShortcutManager
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chungjungsoo.gptmobile.smoke.FeatureSmokeHarness
import dev.chungjungsoo.gptmobile.smoke.SMOKE_REPLY
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Quick ask checklist: shortcuts, the tile, the sheet, and the text-selection draft.
 * Skips until [dev.chungjungsoo.gptmobile.presentation.fastentry.QuickAskActivity] is in the APK.
 */
@RunWith(AndroidJUnit4::class)
@SmokeFeature("quick-ask")
class QuickAskSmokeTest :
    FeatureSmokeHarness(
        markerClass = "dev.chungjungsoo.gptmobile.presentation.fastentry.QuickAskActivity",
        feature = "quick-ask"
    ) {
    @Test
    fun staticShortcutsAndQuickSettingsTileAreRegistered() {
        val shortcuts = context.getSystemService(ShortcutManager::class.java).manifestShortcuts.map { it.id }
        assertTrue(shortcuts.containsAll(listOf("new_chat", "voice", "quick_ask")))
        context.packageManager.getServiceInfo(
            ComponentName(context, "dev.chungjungsoo.gptmobile.presentation.fastentry.RobinQuickSettingsTile"),
            0
        )
        launchMain()
        openNewChat()
        typeAndSend("Remember this chat")
        waitForText(SMOKE_REPLY)
        goBack()
        waitForHome()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            context.getSystemService(ShortcutManager::class.java).dynamicShortcuts.isNotEmpty()
        }
    }

    @Test
    fun sheetStreamsAReplyAndCanContinue() {
        launchQuickAsk(Intent())
        waitForText(featureText("shortcut_new_chat"))
        waitForText(featureText("shortcut_voice"))
        composeRule.onNode(hasSetTextAction()).performTextInput("How many moons?")
        clickText(featureText("quick_ask_send"))
        waitForText(SMOKE_REPLY)
        waitForText(featureText("quick_ask_continue"))
        composeRule.onNode(hasSetTextAction()).performTextInput("And the smaller one?")
        clickText(featureText("quick_ask_send"))
        composeRule.waitUntil(UI_TIMEOUT_MS) { server.completionCount() >= 2 }
        assertTrue(server.requests.count { it.path.contains("chat/completions") } >= 2)
    }

    @Test
    fun textSelectionBecomesTheDraft() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT)
            .setClassName(context, "dev.chungjungsoo.gptmobile.presentation.fastentry.QuickAskActivity")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "selected words")
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        launchQuickAsk(intent)
        waitForText("selected words")
        clickText(featureText("quick_ask_send"))
        waitForText(SMOKE_REPLY)
        waitForText(featureText("quick_ask_replace"))
    }

    @Test
    fun voiceJumpSaysWhenDrivingModeIsMissing() {
        launchQuickAsk(Intent())
        clickText(featureText("shortcut_voice"))
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            val unavailable = runCatching { featureText("voice_mode_unavailable") }.getOrNull()
            val driving = runCatching { featureText("hands_free_open") }.getOrNull()
            (unavailable != null && hasText(unavailable)) ||
                (driving != null && hasText(driving)) ||
                hasText("Start")
        }
    }

    private fun launchQuickAsk(intent: Intent) {
        intent.setClassName(context, "dev.chungjungsoo.gptmobile.presentation.fastentry.QuickAskActivity")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launch(intent)
    }
}
