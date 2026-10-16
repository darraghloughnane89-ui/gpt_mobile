package dev.chungjungsoo.gptmobile.presentation.ui.voice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chungjungsoo.gptmobile.data.voice.VoicePhase
import dev.chungjungsoo.gptmobile.data.voice.VoiceSessionState
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VoiceScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun voiceControls_showTheLiveTranscriptAndInterruptOrMute() {
        var muted = false
        var interrupted = false
        var ended = false
        composeRule.setContent {
            GPTMobileTheme {
                VoiceScreen(
                    state = VoiceSessionState(
                        phase = VoicePhase.SPEAKING,
                        partialTranscript = "What's the weather",
                        replyText = "It is sunny."
                    ),
                    permissionGranted = true,
                    onMute = { muted = true },
                    onUnmute = {},
                    onInterrupt = { interrupted = true },
                    onEnd = { ended = true },
                    onOrbClick = { interrupted = true },
                    onGrantPermission = {}
                )
            }
        }

        composeRule.onNodeWithText("Speaking").assertIsDisplayed()
        composeRule.onNodeWithText("What's the weather").assertIsDisplayed()
        composeRule.onNodeWithText("It is sunny.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Interrupt").performClick()
        composeRule.onNodeWithContentDescription("Mute microphone").performClick()
        composeRule.onNodeWithContentDescription("End voice chat").performClick()

        composeRule.runOnIdle {
            assertTrue(interrupted)
            assertTrue(muted)
            assertTrue(ended)
        }
    }

    @Test
    fun voiceScreen_withoutMicrophonePermission_offersTheGrantAction() {
        var grants = 0
        composeRule.setContent {
            GPTMobileTheme {
                VoiceScreen(
                    state = VoiceSessionState(phase = VoicePhase.IDLE),
                    permissionGranted = false,
                    onMute = {},
                    onUnmute = {},
                    onInterrupt = {},
                    onEnd = {},
                    onOrbClick = {},
                    onGrantPermission = { grants += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Microphone access").assertIsDisplayed()
        composeRule.onNodeWithText("Allow microphone").performClick()
        composeRule.runOnIdle { assertEquals(1, grants) }
    }
}
