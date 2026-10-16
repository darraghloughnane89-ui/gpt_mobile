package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    application = Application::class,
    qualifiers = RobolectricDeviceQualifiers.Pixel7
)
class DesignScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingLines_hasTheFullSet() {
        val lines = RuntimeEnvironment.getApplication().resources.getStringArray(R.array.loading_lines)
        assertEquals(40, lines.size)
        assertEquals(false, lines.any { it.contains("wet leaf") || it.contains("Fluffing") })
    }

    @Test
    fun before_legacyGreenWordmark() {
        capture("before_legacy_green") {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF00A67D))
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "GPT Mobile", color = Color.White)
                Text(
                    text = "The best AI assistant you can get in your smartphone.",
                    color = Color.White,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }

    @Test
    fun after_emptyNest() {
        capture("after_empty_nest") {
            GPTMobileTheme {
                EmptyState(
                    title = "Empty nest",
                    body = "No chats yet. Start one and I’ll perch right here.",
                    mood = MascotMood.Wave,
                    animated = false,
                    actionLabel = "Start a chat",
                    onAction = {}
                )
            }
        }
    }

    @Test
    fun after_chatComposer() {
        capture("after_chat_composer") {
            GPTMobileTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ChatBubble(text = "Anything good on the wind?", role = BubbleRole.User)
                    ChatBubble(
                        text = "Clear enough. Ask the useful question.",
                        role = BubbleRole.Assistant,
                        author = "Ember",
                        streaming = true,
                        animated = false
                    )
                    TypingIndicator(animated = false)
                    ComposerBar(
                        canSend = true,
                        isSending = false,
                        onSend = {},
                        onStop = {},
                        onAttach = {},
                        field = {
                            Text(
                                text = "Ask something useful",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }
        }
    }

    @Test
    fun after_onboarding() {
        capture("after_onboarding") {
            GPTMobileTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    VoiceOrb(
                        amplitude = 0.15f,
                        state = VoiceOrbState.Idle,
                        animated = false,
                        modifier = Modifier.size(180.dp)
                    )
                    Text(
                        text = "Welcome. I’m ${Brand.name()}.",
                        style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                    Text(
                        text = "Voice, bots, and secrets that stay on this phone. Let’s set the nest.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    @Test
    fun after_markAndMascot() {
        capture("after_mark_and_mascot") {
            GPTMobileTheme {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_gpt_mobile_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(160.dp)
                    )
                    Mascot(mood = MascotMood.Wave, animated = false)
                    Image(
                        painter = painterResource(R.drawable.ic_brand_mascot),
                        contentDescription = null,
                        modifier = Modifier.size(120.dp)
                    )
                }
            }
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent(content)
        composeRule.onRoot().captureRoboImage(filePath = "src/test/snapshots/$name.png")
    }
}
