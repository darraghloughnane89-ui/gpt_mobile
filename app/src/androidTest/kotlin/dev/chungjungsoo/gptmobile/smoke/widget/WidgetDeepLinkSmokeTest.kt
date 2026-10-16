package dev.chungjungsoo.gptmobile.smoke.widget

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidTest
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity
import dev.chungjungsoo.gptmobile.smoke.SmokeActivityTest
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import dev.chungjungsoo.gptmobile.smoke.deepLinkResolves
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@SmokeFeature("widget")
class WidgetDeepLinkSmokeTest : SmokeActivityTest("widget") {
    @Test
    fun widgetDeepLink_newChat_opensTheComposer() {
        assumeFeature("widget_new_chat")
        val uri = "dev.chungjungsoo.gptmobile://phone/new-chat"
        assumeTrue("Widget deep link is not registered yet", deepLinkResolves(targetContext, uri))
        startFakeLlm()
        seedFakeProvider()
        launch(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setClass(targetContext, MainActivity::class.java)
        )
        waitForText(targetContext.getString(R.string.ask_a_question), timeoutMs = 45_000)
    }
}
