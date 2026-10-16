package dev.chungjungsoo.gptmobile.smoke.voice

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.qa.FakeRecognitionService
import dev.chungjungsoo.gptmobile.smoke.SmokeActivityTest
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@SmokeFeature("voice")
class VoiceScreenSmokeTest : SmokeActivityTest("voice") {
    @Test
    fun voiceScreen_openAndClose_withFakeRecognizer() {
        val voiceChat = assumeFeature("voice_chat")
        val endVoice = assumeFeature("voice_end")
        val selected = installFakeRecognizer()
        assertTrue(
            "Fake recognizer was not selected: $selected",
            selected.endsWith("FakeRecognitionService")
        )
        grantIfDeclared(Manifest.permission.RECORD_AUDIO)
        launchSeededHome()
        openNewChat()
        clickContentDescription(voiceChat)
        val allow = optionalString("voice_permission_grant")
        if (allow != null && (hasText(allow) || deviceHas(allow))) {
            clickText(allow)
        }
        waitForText(FakeRecognitionService.TRANSCRIPT, timeoutMs = 20_000)
        clickContentDescription(endVoice)
        waitForText(targetContext.getString(R.string.ask_a_question))
    }

    private fun deviceHas(text: String): Boolean = device().findObject(
        androidx.test.uiautomator.UiSelector().text(text)
    ).exists()
}
