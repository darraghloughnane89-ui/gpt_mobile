package dev.chungjungsoo.gptmobile.presentation.ui.obsidian

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ObsidianSyncScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun conflict_keepPhone_invokesCallbackAndShowsGitHubAsDefault() {
        var kept: String? = null
        composeRule.setContent {
            GPTMobileTheme {
                ObsidianSyncScreen(
                    state = ObsidianSyncUiState(
                        status = "1 conflict",
                        conflicts = listOf(
                            ConflictUi(
                                id = "chat-1",
                                title = "Hello",
                                localPreview = "phone text",
                                remotePreview = "vault text",
                                allowKeepBoth = true
                            )
                        ),
                        log = listOf("Synced. Pulled 0, pushed 0, 1 conflicts.")
                    ),
                    onBack = {},
                    onOwnerChange = {},
                    onRepoChange = {},
                    onBranchChange = {},
                    onRootChange = {},
                    onTokenChange = {},
                    onSaveGitHub = {},
                    onSyncNow = {},
                    onShowFolderOption = {},
                    onUseGitHub = {},
                    onPickFolder = {},
                    onKeepPhone = { kept = it },
                    onKeepVault = {},
                    onKeepBoth = {}
                )
            }
        }

        composeRule.onNodeWithText("GitHub repository").assertExists()
        composeRule.onNodeWithText("free GitHub repository", substring = true).assertExists()
        composeRule.onNodeWithText("Use a folder on this phone instead").assertExists()
        composeRule.onNodeWithText("Keep phone").performScrollTo().performClick()
        assertEquals("chat-1", kept)
    }
}
