package dev.chungjungsoo.gptmobile.presentation.ui.bot

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chungjungsoo.gptmobile.data.bot.BuiltInBots
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BotPickerInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun picker_listsBuiltInBots_andReportsSelection() {
        val bots = BuiltInBots.definitions().map { it.bot }
        var selected: String? = "unset"
        composeRule.setContent {
            GPTMobileTheme {
                BotPickerList(
                    bots = bots,
                    selectedBotUid = BuiltInBots.ASSISTANT_UID,
                    onSelect = { selected = it }
                )
            }
        }

        composeRule.onNodeWithText("Assistant").assertExists()
        composeRule.onNodeWithText("Coder").assertExists()
        composeRule.onNodeWithText("No bot").assertExists()
        composeRule.onNodeWithText("Coder").performClick()
        assertEquals(BuiltInBots.CODER_UID, selected)
    }
}
