package dev.chungjungsoo.gptmobile.presentation.phone

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShareTargetScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newChat_click_invokesCallback() {
        var newChatClicks = 0
        composeRule.setContent {
            GPTMobileTheme {
                ShareTargetScreen(
                    preview = "Summarize the attached notes",
                    attachmentSummary = "1 attachment",
                    chats = listOf(ShareChatRow(id = 7, title = "Trip planning")),
                    warnings = emptyList(),
                    isLoading = false,
                    error = null,
                    importing = false,
                    onNewChat = { newChatClicks++ },
                    onChatSelected = {},
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithText("Summarize the attached notes").assertExists()
        composeRule.onNodeWithText("New chat").performClick()
        assertEquals(1, newChatClicks)
    }

    @Test
    fun existingChat_click_returnsChatId() {
        var selectedId = -1
        composeRule.setContent {
            GPTMobileTheme {
                ShareTargetScreen(
                    preview = "",
                    attachmentSummary = "",
                    chats = listOf(ShareChatRow(id = 7, title = "Trip planning")),
                    warnings = emptyList(),
                    isLoading = false,
                    error = null,
                    importing = false,
                    onNewChat = {},
                    onChatSelected = { selectedId = it },
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithText("Trip planning").performClick()
        assertEquals(7, selectedId)
    }
}
