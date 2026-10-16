package dev.chungjungsoo.gptmobile.presentation.ui.provider

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.chungjungsoo.gptmobile.data.gguf.GgufModelRecord
import dev.chungjungsoo.gptmobile.data.provider.RemoteModel
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.ui.gguf.GgufModelChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RemoteModelPickerInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchFiltersTheListAndARowFillsTheManualId() {
        var selected = "grok-4.7"
        composeRule.setContent {
            var query by remember { mutableStateOf("") }
            var manual by remember { mutableStateOf(selected) }
            GPTMobileTheme {
                RemoteModelPicker(
                    query = query,
                    onQueryChange = { query = it },
                    manualModel = manual,
                    onManualModelChange = {
                        manual = it
                        selected = it
                    },
                    models = listOf(
                        RemoteModel(id = "grok-4.7"),
                        RemoteModel(id = "grok-4.6"),
                        RemoteModel(id = "grok-4.3", supportsVision = true)
                    ),
                    isLoading = false,
                    statusMessage = null,
                    onRefresh = {}
                )
            }
        }

        composeRule.onNodeWithTag("remote_model_search").performTextInput("4.3")
        composeRule.onNodeWithTag("remote_model_grok-4.3").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("remote_model_grok-4.7").assertDoesNotExist()
        composeRule.onNodeWithText("Vision").assertIsDisplayed()
        assertEquals("grok-4.3", selected)

        composeRule.onNodeWithTag("remote_model_manual").performTextInput("-custom")
        assertTrue(selected.contains("custom"))
    }

    @Test
    fun ggufChoicesReportTheSelectedModel() {
        var selected = ""
        var managed = false
        val model = GgufModelRecord(
            id = "abc",
            displayName = "Qwen tiny",
            fileName = "abc.gguf",
            sizeBytes = 2048,
            contextSize = 2048,
            temperature = 0.8f
        )
        composeRule.setContent {
            GPTMobileTheme {
                GgufModelChoices(
                    models = listOf(model),
                    selectedId = selected,
                    onSelected = { selected = it.id },
                    onManage = { managed = true }
                )
            }
        }

        composeRule.onNodeWithText("Qwen tiny").performClick()
        composeRule.onNodeWithText("Manage GGUF models").performClick()
        assertEquals("abc", selected)
        assertTrue(managed)
    }
}
