package dev.chungjungsoo.gptmobile.smoke.bots

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.smoke.SmokeActivityTest
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@SmokeFeature("bots")
class BotSwitchSmokeTest : SmokeActivityTest("bots") {
    @Test
    fun botSwitch_selectsCoderFromTheChatSheet() {
        launchSeededHome()
        openNewChat()
        clickContentDescription(targetContext.getString(R.string.choose_bot))
        clickText("Coder")
        waitForContent(targetContext.getString(R.string.current_bot, "Coder"))
    }
}
