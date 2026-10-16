package dev.chungjungsoo.gptmobile.smoke.canvas

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.smoke.FakeChatServer
import dev.chungjungsoo.gptmobile.smoke.FeatureSmokeHarness
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Canvas checklist: open a document, edit it, accept a model proposal, and export.
 * Skips until [dev.chungjungsoo.gptmobile.data.canvas.FileCanvasStore] is in the APK.
 */
@RunWith(AndroidJUnit4::class)
@SmokeFeature("canvas")
class CanvasSmokeTest :
    FeatureSmokeHarness(
        markerClass = "dev.chungjungsoo.gptmobile.data.canvas.FileCanvasStore",
        feature = "canvas"
    ) {
    @Test
    fun menuOpensABlankCanvasThatCanBeCopiedAndExported() {
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            )
            launchMain()
            openNewChat()
            clickContentDescription(context.getString(R.string.options))
            clickText(featureText("new_canvas"))
            waitForText(featureText("canvas_empty"))
            composeRule.onNodeWithText(featureText("canvas_untitled")).performTextReplacement("Smoke canvas")
            waitForText("Smoke canvas")
            clickContentDescription(featureText("canvas_copy"))
            clickContentDescription(featureText("canvas_export"))
            clickText(featureText("canvas_export_markdown"))
            Intents.intended(hasAction(Intent.ACTION_CHOOSER))
            waitForText(featureText("canvas_rewrite_selection"))
        } finally {
            Intents.release()
        }
    }

    @Test
    fun modelFenceOpensTheDocumentAndAcceptCopiesTheProposalForward() {
        server.script = FakeChatServer.Script.CANVAS_OPEN
        launchMain()
        openNewChat()
        typeAndSend("Open a canvas")
        composeRule.waitUntil(UI_TIMEOUT_MS) {
            hasText("Smoke canvas") || hasText(featureText("open_in_canvas"))
        }
        if (hasText(featureText("open_in_canvas"))) clickText(featureText("open_in_canvas"))
        waitForText("Smoke canvas")
        server.script = FakeChatServer.Script.CANVAS_PROPOSE
        if (hasText(featureText("canvas_chat"))) clickText(featureText("canvas_chat"))
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Revise the canvas")
        clickContentDescription(context.getString(R.string.send))
        waitForText(featureText("canvas_accept"))
        clickText(featureText("canvas_accept"))
        clickText(featureText("canvas_versions"))
        waitForText(featureText("canvas_version_model"))
    }
}
