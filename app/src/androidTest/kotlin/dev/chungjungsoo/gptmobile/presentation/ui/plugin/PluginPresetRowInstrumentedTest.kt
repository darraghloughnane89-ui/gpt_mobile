package dev.chungjungsoo.gptmobile.presentation.ui.plugin

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chungjungsoo.gptmobile.data.plugin.PluginCatalog
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PluginPresetRowInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enableClickPassesThePresetUrl() {
        var url: String? = null
        composeRule.setContent {
            GPTMobileTheme {
                PluginPresetRow(
                    preset = PluginCatalog.gmail,
                    installed = false,
                    onEnable = { selectedUrl, _ -> url = selectedUrl }
                )
            }
        }

        composeRule.onNodeWithText("Enable").performClick()
        assertEquals("https://gmailmcp.googleapis.com/mcp/v1", url)
    }
}
