package dev.chungjungsoo.gptmobile.presentation.a11y

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.presentation.common.SettingItem
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.ui.setting.CrashLogContent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccessibilitySemanticsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingItemKeepsItsDescriptionAtDoubleFontScale() {
        val description = "Saved chats API keys and preferences stay readable when the system font is doubled " +
            "across every settings row without clipping the supporting copy"
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2f, fontScale = 2f)) {
                GPTMobileTheme {
                    Box(Modifier.width(200.dp)) {
                        SettingItem(
                            title = "Storage",
                            description = description,
                            onItemClick = {},
                            showTrailingIcon = false,
                            showLeadingIcon = false
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(description).assertExists().assertHeightIsAtLeast(200.dp)
    }

    @Test
    fun crashLogScreenExposesActionsAndAnEmptyState() {
        var exported = false
        var cleared = false
        var navigated = false
        composeRule.setContent {
            GPTMobileTheme {
                CrashLogContent(
                    logText = "",
                    onNavigationClick = { navigated = true },
                    onExport = { exported = true },
                    onClear = { cleared = true }
                )
            }
        }

        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        composeRule.onNodeWithContentDescription("Go back").assertHasClickAction().performClick()
        assertTrue(navigated)
        composeRule.onNodeWithText("Export").assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        assertTrue(exported)
        composeRule.onNodeWithText("No crashes saved on this device.").assertExists()
        composeRule.onNodeWithText("Clear").assertHasClickAction().assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Delete the saved crash log from this device?").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        assertFalse(cleared)
    }
}
