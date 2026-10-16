package dev.chungjungsoo.gptmobile.smoke.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chungjungsoo.gptmobile.smoke.FeatureSmokeHarness
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Wake word and driving-mode checklist.
 * Skips until [dev.chungjungsoo.gptmobile.presentation.ui.voice.HandsFreeViewModel] is in the APK.
 */
@RunWith(AndroidJUnit4::class)
@SmokeFeature("voice")
class VoiceDrivingSmokeTest :
    FeatureSmokeHarness(
        markerClass = "dev.chungjungsoo.gptmobile.presentation.ui.voice.HandsFreeViewModel",
        feature = "voice"
    ) {
    @Test
    fun drivingModeShowsLargeControlsWithoutStartingTheMicrophone() {
        launchMain()
        openSettings()
        clickTextContaining("Hey Robin")
        waitForText(featureText("hands_free_open"))
        waitForText("battery", substring = true)
        clickText(featureText("hands_free_open"))
        waitForText(featureText("hands_free_start"))
        waitForText(featureText("hands_free_mute"))
        waitForText(featureText("hands_free_end"))
        waitForText(featureText("hands_free_idle"), substring = true)
    }

    @Test
    fun wakeSwitchPostsTheMicrophoneNotificationAndStopClearsIt() {
        grantIfDeclared(Manifest.permission.RECORD_AUDIO)
        launchMain()
        openSettings()
        clickTextContaining("Hey Robin")
        waitForText(featureText("hands_free_open"))
        composeRule.onNode(isToggleable()).performClick()
        val posted = waitForNotification { notification ->
            notification.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.contains("Microphone") == true
        }
        check(posted.notification.actions.orEmpty().any { it.title?.contains("Stop") == true })
        composeRule.onNode(isToggleable()).performClick()
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.none { notification ->
                notification.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.contains("Microphone") == true
            }
        }
    }
}
