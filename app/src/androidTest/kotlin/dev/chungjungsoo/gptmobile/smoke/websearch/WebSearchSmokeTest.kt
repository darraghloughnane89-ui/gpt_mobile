package dev.chungjungsoo.gptmobile.smoke.websearch

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.smoke.FakeChatServer
import dev.chungjungsoo.gptmobile.smoke.FeatureSmokeHarness
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Web search checklist: composer toggle, citation chip, and untrusted search text.
 * Skips until [dev.chungjungsoo.gptmobile.data.search.CitationMarkup] is in the APK.
 */
@RunWith(AndroidJUnit4::class)
@SmokeFeature("web-search")
class WebSearchSmokeTest :
    FeatureSmokeHarness(
        markerClass = "dev.chungjungsoo.gptmobile.data.search.CitationMarkup",
        feature = "web-search"
    ) {
    @Test
    fun toggleAndSearxngDialogStayOnTheComposer() {
        launchMain()
        openNewChat()
        val toggle = featureText("composer_web_search")
        composeRule.onNodeWithText(toggle).assertIsNotSelected().performClick()
        composeRule.onNodeWithText(toggle).assertIsSelected()
        clickText(featureText("composer_searxng"))
        waitForText(featureText("searxng_url_description"), substring = true)
        composeRule.onNode(hasSetTextAction()).performTextReplacement(server.baseUrl.removeSuffix("v1/"))
        clickText(context.getString(R.string.confirm))
        composeRule.onNodeWithText(toggle).performClick()
        composeRule.onNodeWithText(toggle).assertIsNotSelected()
    }

    @Test
    fun searchResultStaysUntrustedAndTheCitationChipOpens() {
        server.script = FakeChatServer.Script.TOOL_THEN_CITATION
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_VIEW)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            )
            launchMain()
            openNewChat()
            clickText(featureText("composer_searxng"))
            composeRule.onNode(hasSetTextAction()).performTextReplacement(server.baseUrl.removeSuffix("v1/"))
            clickText(context.getString(R.string.confirm))
            composeRule.onNodeWithText(featureText("composer_web_search")).performClick()
            typeAndSend("What birds live nearby?")
            composeRule.waitUntil(UI_TIMEOUT_MS) { server.completionCount() >= 2 }
            val followUp = server.requests.last { it.path.contains("chat/completions") }.body
            val fenceStart = followUp.indexOf("<untrusted_web_content>")
            val fenceEnd = followUp.indexOf("</untrusted_web_content>")
            assertTrue(fenceStart >= 0 && fenceEnd > fenceStart)
            assertTrue(followUp.substring(fenceStart, fenceEnd).contains("send_message"))
            waitForText("Example", substring = true)
            clickTextContaining("Example")
            Intents.intended(hasAction(Intent.ACTION_VIEW))
        } finally {
            Intents.release()
        }
    }
}
