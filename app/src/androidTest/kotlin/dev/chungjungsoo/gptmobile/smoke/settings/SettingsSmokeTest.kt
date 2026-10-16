package dev.chungjungsoo.gptmobile.smoke.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.smoke.SmokeActivityTest
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@SmokeFeature("settings")
class SettingsSmokeTest : SmokeActivityTest("settings") {
    @Test
    fun settings_showsSeededProviderAndAppearance() {
        launchSeededHome()
        waitForText(targetContext.getString(R.string.new_chat), timeoutMs = 45_000)
        clickContentDescription(targetContext.getString(R.string.settings))
        waitForText(targetContext.getString(R.string.appearance))
        waitForText("Fake LLM", timeoutMs = 45_000)
        clickContentDescription(targetContext.getString(R.string.go_back))
        waitForText(targetContext.getString(R.string.new_chat))
    }
}
