package dev.chungjungsoo.gptmobile.smoke.share

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.chungjungsoo.gptmobile.smoke.SmokeActivityTest
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import dev.chungjungsoo.gptmobile.smoke.launchable
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@SmokeFeature("share")
class ShareSheetSmokeTest : SmokeActivityTest("share") {
    @Test
    fun shareSheet_textIntent_showsPreviewAndNewChat() {
        val newChat = assumeFeature("share_new_chat")
        val activity = launchable(
            targetContext,
            "dev.chungjungsoo.gptmobile.presentation.phone.ShareTargetActivity"
        )
        assumeTrue("Share activity is not in this build yet", activity != null)
        startFakeLlm()
        seedFakeProvider()
        val share = checkNotNull(activity).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, SHARED_TEXT)
        }
        launch(share)
        waitForText(SHARED_TEXT, timeoutMs = 20_000)
        waitForText(newChat)
    }

    private companion object {
        const val SHARED_TEXT = "Smoke shared sentence"
    }
}
