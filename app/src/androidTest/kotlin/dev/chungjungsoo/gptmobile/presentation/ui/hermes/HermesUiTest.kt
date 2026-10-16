package dev.chungjungsoo.gptmobile.presentation.ui.hermes

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import dev.chungjungsoo.gptmobile.data.hermes.HermesDeliveryStatus
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.ui.chat.UserChatBubble
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HermesUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsShowsDefaultProfileAndConnectionTest() {
        composeRule.setContent {
            GPTMobileTheme {
                HermesSettingsScreen(
                    state = HermesSettingsUiState(),
                    onEndpointChange = {},
                    onProfileChange = {},
                    onTokenChange = {},
                    onChatModelChange = {},
                    onTestConnection = {},
                    onSave = {},
                    onOpenThread = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithText("Hermes profile. The default is cbre-admin.").assertExists()
        composeRule.onNodeWithText("Test connection").assertExists()
        composeRule.onNodeWithText("cbre-admin").assertExists()
    }

    @Test
    fun threadShowsQueuedDeliveryStatus() {
        composeRule.setContent {
            GPTMobileTheme {
                HermesThreadScreen(
                    state = HermesThreadUiState(
                        title = "Hermes",
                        turns = listOf(
                            HermesTurnUi(id = "user", isUser = true, text = "Check the lease"),
                            HermesTurnUi(
                                id = "assistant",
                                isUser = false,
                                text = "",
                                status = HermesDeliveryStatus.QUEUED
                            )
                        )
                    ),
                    onBack = {},
                    onRetry = {},
                    onNewThread = {}
                )
            }
        }

        composeRule.onNodeWithText("Check the lease").assertExists()
        composeRule.onNodeWithText("Queued").assertExists()
    }

    @Test
    fun userMessageLongPressOffersSendToHermes() {
        var sent = false
        composeRule.setContent {
            GPTMobileTheme {
                UserChatBubble(
                    text = "Question",
                    canEdit = true,
                    onSendToHermes = { sent = true }
                )
            }
        }

        composeRule.onNodeWithText("Question")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
            .performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.onNodeWithText("Send to Hermes").performClick()
        assertTrue(sent)
    }

    @Test
    fun askHermesDialogSendsComposerTask() {
        var sent = ""
        composeRule.setContent {
            GPTMobileTheme {
                HermesAskDialog(
                    initialText = "summarize the thread",
                    onDismiss = {},
                    onSend = { sent = it }
                )
            }
        }

        composeRule.onNodeWithText("Send to Hermes").performClick()
        assertEquals("summarize the thread", sent)
    }
}
