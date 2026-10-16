package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chungjungsoo.gptmobile.data.bot.BotDraft
import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.data.model.ThemeMode
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import dev.chungjungsoo.gptmobile.presentation.ui.bot.BotEditorForm
import dev.chungjungsoo.gptmobile.presentation.ui.bot.BotListPane
import dev.chungjungsoo.gptmobile.presentation.ui.bot.BotPickerList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    application = Application::class,
    qualifiers = RobolectricDeviceQualifiers.Pixel7
)
class FeatureRolloutScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun before_botsEmpty() {
        capture("before_bots_empty") {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(24.dp)
                ) {
                    Text(text = "Bots", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "No bots yet. Create one to give a chat its own persona.",
                        modifier = Modifier.padding(top = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Test
    fun before_botsList() {
        capture("before_bots_list") {
            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "Bots",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(24.dp)
                    )
                    ListItem(
                        headlineContent = { Text("Ember") },
                        supportingContent = { Text("Short answers. Dry humour.") }
                    )
                    ListItem(
                        headlineContent = { Text("Field notes") },
                        supportingContent = { Text("Turn a site visit into a tidy report.") }
                    )
                }
            }
        }
    }

    @Test
    fun after_botsEmpty_dark() = themed("after_bots_empty_dark", dark = true) { BotsEmpty() }

    @Test
    fun after_botsEmpty_light() = themed("after_bots_empty_light", dark = false) { BotsEmpty() }

    @Test
    fun after_botsList_dark() = themed("after_bots_list_dark", dark = true) { BotsList() }

    @Test
    fun after_botsList_light() = themed("after_bots_list_light", dark = false) { BotsList() }

    @Test
    fun after_botEditor_dark() = themed("after_bot_editor_dark", dark = true) { Editor() }

    @Test
    fun after_botEditor_light() = themed("after_bot_editor_light", dark = false) { Editor() }

    @Test
    fun after_botSwitcher_dark() = themed("after_bot_switcher_dark", dark = true) { Switcher() }

    @Test
    fun after_botSwitcher_light() = themed("after_bot_switcher_light", dark = false) { Switcher() }

    @Test
    fun after_settingsEmpty_dark() = themed("after_settings_empty_dark", dark = true) { SettingsEmpty() }

    @Test
    fun after_settingsEmpty_light() = themed("after_settings_empty_light", dark = false) { SettingsEmpty() }

    @Composable
    private fun BotsEmpty() {
        BotListPane(
            bots = emptyList(),
            canRestoreBuiltIns = false,
            onCreateClick = {},
            onEditClick = {},
            onDuplicate = {},
            onReset = {},
            onDelete = {},
            onRestoreBuiltIns = {},
            animated = false
        )
    }

    @Composable
    private fun BotsList() {
        BotListPane(
            bots = sampleBots(),
            canRestoreBuiltIns = false,
            onCreateClick = {},
            onEditClick = {},
            onDuplicate = {},
            onReset = {},
            onDelete = {},
            onRestoreBuiltIns = {},
            animated = false
        )
    }

    @Composable
    private fun Editor() {
        BotEditorForm(
            draft = BotDraft(
                name = "Ember",
                avatarEmoji = "*",
                avatarColor = "#B42318",
                instructions = "Short answers. Dry humour. No corporate filler.",
                starterPrompts = listOf("What should I ask?")
            ),
            platforms = emptyList(),
            searchConnections = emptyList(),
            mcpConnections = emptyList(),
            errorMessage = null,
            onDraftChange = {},
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        )
    }

    @Composable
    private fun Switcher() {
        BotPickerList(
            bots = sampleBots(),
            selectedBotUid = "ember",
            onSelect = {},
            modifier = Modifier.padding(top = 24.dp)
        )
    }

    @Composable
    private fun SettingsEmpty() {
        Column(modifier = Modifier.fillMaxSize()) {
            EmptyState(
                title = "No platforms yet",
                body = "Add one and I’ll keep the keys on this phone.",
                mood = MascotMood.Peek,
                animated = false,
                actionLabel = "Add platform",
                onAction = {}
            )
        }
    }

    private fun sampleBots(): List<Bot> = listOf(
        Bot(
            botUid = "ember",
            name = "Ember",
            avatarEmoji = "*",
            avatarColor = "#B42318",
            instructions = "Short answers. Dry humour.",
            builtin = true
        ),
        Bot(
            botUid = "field",
            name = "Field notes",
            avatarEmoji = "#",
            avatarColor = "#8A4F00",
            instructions = "Turn a site visit into a tidy report.",
            builtin = false
        )
    )

    private fun themed(name: String, dark: Boolean, content: @Composable () -> Unit) {
        capture(name) {
            GPTMobileTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    content()
                }
            }
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent(content)
        composeRule.onRoot().captureRoboImage(filePath = "src/test/snapshots/$name.png")
    }
}
