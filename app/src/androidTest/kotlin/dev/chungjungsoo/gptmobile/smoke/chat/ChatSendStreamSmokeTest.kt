package dev.chungjungsoo.gptmobile.smoke.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.chungjungsoo.gptmobile.smoke.SMOKE_REPLY
import dev.chungjungsoo.gptmobile.smoke.SmokeActivityTest
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@SmokeFeature("chat")
class ChatSendStreamSmokeTest : SmokeActivityTest("chat") {
    @Test
    fun chatSendStream_fakeProvider_showsStreamedReply() {
        launchSeededHome()
        openNewChat()
        typeAndSend("ping")
        waitForText(SMOKE_REPLY, timeoutMs = 45_000, substring = true)
        assertTrue(
            "Fake provider was not called. paths=$requestedPaths",
            requestedPaths.any { path -> path.contains("chat/completions") }
        )
    }
}
