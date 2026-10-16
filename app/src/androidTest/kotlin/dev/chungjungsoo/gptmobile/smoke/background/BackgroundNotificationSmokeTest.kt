package dev.chungjungsoo.gptmobile.smoke.background

import android.app.Activity
import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.provider.Settings
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chungjungsoo.gptmobile.smoke.FeatureSmokeHarness
import dev.chungjungsoo.gptmobile.smoke.SMOKE_REPLY
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Background generation checklist: progress, Stop, completion reply, and a blocked channel.
 * Skips until [dev.chungjungsoo.gptmobile.presentation.service.GenerationReplyReceiver] is in the APK.
 */
@RunWith(AndroidJUnit4::class)
@SmokeFeature("background")
class BackgroundNotificationSmokeTest :
    FeatureSmokeHarness(
        markerClass = "dev.chungjungsoo.gptmobile.presentation.service.GenerationReplyReceiver",
        feature = "background"
    ) {
    @Test
    fun settingsOpenTheProgressAndFinishedReplyChannels() {
        Intents.init()
        try {
            Intents.intending(hasAction(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            )
            launchMain()
            openSettings()
            clickText(featureText("generation_progress_channel_settings"))
            Intents.intended(channelIntent("agent_runs"))
            clickText(featureText("generation_complete_channel_settings"))
            Intents.intended(channelIntent("generation_complete"))
        } finally {
            Intents.release()
        }
    }

    @Test
    fun backgroundedReplyShowsProgressThenACompletionReply() {
        server.responseDelayMs = 4_000
        launchMain()
        openNewChat()
        typeAndSend("Keep going")
        val progress = waitForNotification { notification ->
            notification.notification.channelId == "agent_runs"
        }
        assertTrue(progress.notification.actions.orEmpty().any { it.title?.contains("Stop") == true })
        pressHome()
        val done = waitForNotification(timeoutMs = 20_000) { notification ->
            notification.notification.channelId == "generation_complete"
        }
        val text = done.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        assertTrue(text.contains(SMOKE_REPLY) || done.notification.actions.orEmpty().any { it.title == "Reply" })
        relaunchMain()
        waitForText(SMOKE_REPLY)
    }

    @Test
    fun blockedCompletionChannelStillFinishesTheReply() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("generation_complete", "Finished replies", NotificationManager.IMPORTANCE_NONE)
        )
        server.responseDelayMs = 1_500
        launchMain()
        openNewChat()
        typeAndSend("Finish quietly")
        waitForNotification { it.notification.channelId == "agent_runs" }
        pressHome()
        composeRule.waitUntil(20_000) { server.completionCount() >= 1 }
        val completion = manager.activeNotifications.any { it.notification.channelId == "generation_complete" }
        assertTrue(!completion)
        relaunchMain()
        waitForText(SMOKE_REPLY)
    }

    private fun channelIntent(channelId: String) = allOf(
        hasAction(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS),
        hasExtra(Settings.EXTRA_CHANNEL_ID, channelId)
    )
}
