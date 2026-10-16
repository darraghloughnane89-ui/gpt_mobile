package dev.chungjungsoo.gptmobile.presentation.ui.project

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.chungjungsoo.gptmobile.data.project.ProjectColors
import dev.chungjungsoo.gptmobile.data.project.ProjectIcons
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Rule
import org.junit.Test

class ProjectEditorInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun projectEditor_showsNameInstructionsAndSaveAction() {
        composeRule.setContent {
            GPTMobileTheme {
                Scaffold { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        TextButton(onClick = {}) {
                            Text("Save")
                        }
                        ProjectEditorForm(
                            name = "Research",
                            instructions = "Cite the notes.",
                            icon = ProjectIcons.FOLDER,
                            color = ProjectColors.palette.first(),
                            onNameChange = {},
                            onInstructionsChange = {},
                            onIconChange = {},
                            onColorChange = {},
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Project name").assertIsDisplayed()
        composeRule.onNodeWithText("Shared instructions").assertIsDisplayed()
        composeRule.onNodeWithText("Research").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsDisplayed()
    }
}
