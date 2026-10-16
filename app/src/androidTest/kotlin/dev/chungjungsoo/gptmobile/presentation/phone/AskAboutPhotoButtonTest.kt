package dev.chungjungsoo.gptmobile.presentation.phone

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AskAboutPhotoButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun askAboutPhoto_click_invokesCallback() {
        var clicked = false
        composeRule.setContent {
            GPTMobileTheme {
                AskAboutPhotoButton(onClick = { clicked = true })
            }
        }

        composeRule.onNodeWithText("Ask about a photo").performClick()
        assertTrue(clicked)
    }
}
