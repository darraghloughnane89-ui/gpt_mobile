package dev.chungjungsoo.gptmobile.presentation.ui.privacy

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chungjungsoo.gptmobile.data.memory.Memory
import dev.chungjungsoo.gptmobile.data.memory.MemorySource
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.ui.memory.MemoryScreenContent
import dev.chungjungsoo.gptmobile.presentation.ui.memory.MemorySuggestionDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PrivacyScreensInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lockedGate_hidesContentAndUnlockRequestsPrompt() {
        var unlocks = 0
        composeRule.setContent {
            GPTMobileTheme {
                AppLockGateContent(
                    locked = true,
                    deviceLockAvailable = true,
                    onUnlock = { unlocks += 1 },
                    onDisableUnavailable = {},
                    content = { Text("Secret chat") }
                )
            }
        }

        composeRule.onNodeWithText("Secret chat").assertDoesNotExist()
        composeRule.onNodeWithText("Unlock").performClick()
        assertEquals(1, unlocks)
    }

    @Test
    fun lockedGate_unavailableDevice_canContinueWithoutLock() {
        var disabled = false
        composeRule.setContent {
            GPTMobileTheme {
                AppLockGateContent(
                    locked = true,
                    deviceLockAvailable = false,
                    onUnlock = {},
                    onDisableUnavailable = { disabled = true },
                    content = { Text("Secret chat") }
                )
            }
        }

        composeRule.onNodeWithText("Continue without app lock").performClick()
        assertTrue(disabled)
    }

    @Test
    fun memorySuggestion_confirm_invokesCallback() {
        var confirmed = false
        composeRule.setContent {
            GPTMobileTheme {
                MemorySuggestionDialog(
                    fact = "I live in Dublin",
                    onConfirm = { confirmed = true },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("I live in Dublin").assertExists()
        composeRule.onNodeWithText("Remember").performClick()
        assertTrue(confirmed)
    }

    @Test
    fun memoryScreen_delete_invokesCallback() {
        var deleted: Long? = null
        composeRule.setContent {
            GPTMobileTheme {
                MemoryScreenContent(
                    memories = listOf(
                        Memory(
                            id = 7,
                            text = "I prefer tea",
                            source = MemorySource.EXPLICIT,
                            createdAt = 1,
                            updatedAt = 1
                        )
                    ),
                    globallyPaused = false,
                    onBack = {},
                    onGloballyPaused = {},
                    onPaused = { _, _ -> },
                    onUpdateText = { _, _ -> },
                    onAdd = {},
                    onDelete = { deleted = it },
                    onClearAll = {}
                )
            }
        }

        composeRule.onNodeWithText("I prefer tea").assertExists()
        composeRule.onNodeWithContentDescription("Delete memory").performClick()
        assertEquals(7L, deleted)
    }
}
