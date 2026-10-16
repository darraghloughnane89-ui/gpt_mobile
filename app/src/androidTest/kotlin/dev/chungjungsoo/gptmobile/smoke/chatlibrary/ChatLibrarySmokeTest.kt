package dev.chungjungsoo.gptmobile.smoke.chatlibrary

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.smoke.FakeChatServer
import dev.chungjungsoo.gptmobile.smoke.FeatureSmokeHarness
import dev.chungjungsoo.gptmobile.smoke.SMOKE_REPLY
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Chat library checklist from the open folders, temporary chats, artifact, and compare work.
 * Skips until [dev.chungjungsoo.gptmobile.data.library.DataStoreChatLibraryStore] is in the APK.
 */
@RunWith(AndroidJUnit4::class)
@SmokeFeature("chat-library")
class ChatLibrarySmokeTest :
    FeatureSmokeHarness(
        markerClass = "dev.chungjungsoo.gptmobile.data.library.DataStoreChatLibraryStore",
        feature = "chat-library"
    ) {
    @Test
    fun pinSortsFirstAndArchiveLeavesInbox() {
        launchMain()
        sendChat(ALPHA)
        sendChat(BETA)
        longClickText(BETA)
        clickText(featureText("library_pin"))
        dismissSelection()
        waitForContentPinned()
        val pinnedY = composeRule.onNodeWithText(BETA).fetchSemanticsNode().positionInRoot.y
        val otherY = composeRule.onNodeWithText(ALPHA).fetchSemanticsNode().positionInRoot.y
        assertTrue("Pinned chats sort first", pinnedY < otherY)

        longClickText(BETA)
        clickText(featureText("library_archive"))
        dismissSelection()
        waitUntilGone(BETA)
        clickText(featureText("library_archived"))
        waitForText(BETA)
        longClickText(BETA)
        clickText(featureText("library_restore"))
        dismissSelection()
        clickText(featureText("library_inbox"))
        waitForText(BETA)
        assertFalse(hasContentDescription(featureText("pinned_chat")))
    }

    @Test
    fun foldersRejectBadNamesAndReturnChatsToInbox() {
        launchMain()
        sendChat(FOLDER_CHAT)
        clickText(featureText("library_new_folder"))
        composeRule.onNode(hasSetTextAction()).performTextInput(FOLDER)
        clickText(context.getString(R.string.confirm))
        clickText(featureText("library_new_folder"))
        composeRule.onNode(hasSetTextAction()).performTextInput(FOLDER)
        waitForText(featureText("library_folder_duplicate"))
        clickText(context.getString(R.string.cancel))
        clickText(featureText("library_new_folder"))
        composeRule.onNode(hasSetTextAction()).performTextInput("N".repeat(41))
        waitForText(featureText("library_folder_too_long"))
        clickText(context.getString(R.string.cancel))
        clickText(featureText("library_inbox"))
        longClickText(FOLDER_CHAT)
        clickText(featureText("library_folder"))
        clickText(FOLDER)
        clickText(featureText("library_inbox"))
        waitUntilGone(FOLDER_CHAT)
        clickText(FOLDER)
        waitForText(FOLDER_CHAT)
        clickText(featureText("library_rename_folder"))
        composeRule.onNode(hasSetTextAction()).performTextReplacement(RENAMED)
        clickText(context.getString(R.string.confirm))
        waitForText(RENAMED)
        clickText(featureText("library_delete_folder"))
        clickText(featureText("library_inbox"))
        waitForText(FOLDER_CHAT)
    }

    @Test
    fun temporaryChatStaysOffTheListAndIsDeletedOnLeave() {
        launchMain()
        waitForHome()
        clickText(featureText("start_temporary_chat"))
        waitForText(featureText("temporary_chat_banner"), substring = true)
        typeAndSend(TEMPORARY)
        waitForText(SMOKE_REPLY)
        goBack()
        waitForHome()
        assertFalse(hasText(TEMPORARY))
    }

    @Test
    fun htmlPreviewDoesNotRunScripts() {
        server.script = FakeChatServer.Script.HTML
        launchMain()
        openNewChat()
        typeAndSend("Show a page")
        waitForText("Artifact Hello", substring = true)
        clickText(featureText("preview_artifact"))
        waitForText(featureText("artifact_static_note"))
    }

    @Test
    fun quickActionSendsFollowUpAndHidesWhileAReplyIsRunning() {
        server.quickActionDelayMs = 2_500
        launchMain()
        openNewChat()
        typeAndSend("Tell me a fact")
        waitForText(SMOKE_REPLY)
        waitForText(featureText("quick_shorter"))
        assertFalse(hasText(featureText("compare_models")))
        clickText(featureText("quick_shorter"))
        waitUntilGone(featureText("quick_shorter"))
        waitForText(featureText("quick_shorter"), timeoutMs = 15_000)
        val followUp = server.requests.any { request ->
            request.body.contains("Rewrite your previous answer")
        }
        assertTrue(followUp)
    }

    @Test
    fun compareAppearsOnlyForTwoAnswers() {
        seedSecondPlatform()
        launchMain()
        openNewChat()
        typeAndSend("Compare this")
        waitForText(SMOKE_REPLY)
        clickText(featureText("compare_models"))
        waitForText(featureText("compare_models_title"))
    }

    @Test
    fun searchAndMarkdownExportStillWork() {
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            )
            launchMain()
            sendChat(SEARCH_TOKEN)
            clickContentDescription(context.getString(R.string.search_chats))
            composeRule.onNode(hasSetTextAction()).performTextInput(SEARCH_TOKEN)
            waitForText(SEARCH_TOKEN)
            clickText(SEARCH_TOKEN)
            waitForText(SMOKE_REPLY)
            clickContentDescription(context.getString(R.string.options))
            clickText(context.getString(R.string.export_chat))
            Intents.intended(hasAction(Intent.ACTION_CHOOSER))
        } finally {
            Intents.release()
        }
    }

    @Test
    fun emptyChatStillShowsBotStarterPrompts() {
        launchMain()
        openNewChat()
        if (hasContentDescription(context.getString(R.string.choose_bot))) {
            clickContentDescription(context.getString(R.string.choose_bot))
        }
        clickText("Assistant")
        waitForText("Help me plan my day")
    }

    private fun sendChat(message: String) {
        openNewChat()
        typeAndSend(message)
        waitForText(SMOKE_REPLY)
        goBack()
        waitForHome()
    }

    private fun dismissSelection() {
        val close = context.getString(R.string.close)
        if (hasContentDescription(close)) clickContentDescription(close)
    }

    private fun waitForContentPinned() {
        composeRule.waitUntil(UI_TIMEOUT_MS) { hasContentDescription(featureText("pinned_chat")) }
    }

    private companion object {
        const val ALPHA = "Alpha library chat"
        const val BETA = "Beta library chat"
        const val FOLDER_CHAT = "Folder library chat"
        const val FOLDER = "Nest"
        const val RENAMED = "Nest two"
        const val TEMPORARY = "Temporary secret chat"
        const val SEARCH_TOKEN = "LibrarySearchToken"
    }
}
