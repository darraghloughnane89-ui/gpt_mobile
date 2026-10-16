package dev.chungjungsoo.gptmobile.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionReducerTest {
    private val reducer = VoiceSessionReducer()

    @Test
    fun finalUtterance_whileListening_submitsAndKeepsVadForBargeIn() {
        val listening = reducer.reduce(VoiceSessionState(), VoiceEvent.Start).state

        val transition = reducer.reduce(listening, VoiceEvent.FinalUtterance("  What is the weather?  "))

        assertEquals(VoicePhase.THINKING, transition.state.phase)
        assertEquals("What is the weather?", transition.state.lastUserUtterance)
        assertEquals(
            listOf(
                VoiceEffect.StopListening,
                VoiceEffect.StartVad,
                VoiceEffect.SubmitTurn("What is the weather?")
            ),
            transition.effects
        )
    }

    @Test
    fun replyProgress_streamsCompleteSentencesIntoSpeech() {
        val thinking = reducer.reduce(VoiceSessionState(), VoiceEvent.Start).state.let {
            reducer.reduce(it, VoiceEvent.FinalUtterance("Hello")).state
        }

        val transition = reducer.reduce(
            thinking,
            VoiceEvent.ReplyProgress(
                fullText = "It is sunny. More soon",
                sentences = listOf("It is sunny."),
                replyComplete = false
            )
        )

        assertEquals(VoicePhase.SPEAKING, transition.state.phase)
        assertEquals(listOf(VoiceEffect.Speak("It is sunny.")), transition.effects)
        assertEquals("It is sunny.", transition.state.recentlySpoken)
    }

    @Test
    fun bargeIn_whileSpeaking_stopsSpeechBeforeListeningResumes() {
        val speaking = speakingState()

        val transition = reducer.reduce(speaking, VoiceEvent.BargeIn)

        assertEquals(VoicePhase.LISTENING, transition.state.phase)
        assertEquals(1, transition.state.bargeInCount)
        assertEquals(VoiceEffect.StopSpeaking, transition.effects.first())
        assertTrue(transition.effects.contains(VoiceEffect.CancelReply))
        assertTrue(transition.effects.contains(VoiceEffect.StartListening))
    }

    @Test
    fun interrupt_whileSpeaking_stopsSpeechWithoutCountingBargeIn() {
        val transition = reducer.reduce(speakingState(), VoiceEvent.Interrupt)

        assertEquals(VoicePhase.LISTENING, transition.state.phase)
        assertEquals(0, transition.state.bargeInCount)
        assertEquals(VoiceEffect.StopSpeaking, transition.effects.first())
    }

    @Test
    fun mute_blocksTheMicrophoneWithoutStoppingSpeech() {
        val transition = reducer.reduce(speakingState(), VoiceEvent.Mute)

        assertTrue(transition.state.muted)
        assertEquals(VoicePhase.SPEAKING, transition.state.phase)
        assertTrue(transition.effects.contains(VoiceEffect.StopListening))
        assertTrue(transition.effects.contains(VoiceEffect.StopVad))
        assertTrue(transition.effects.none { it == VoiceEffect.StopSpeaking })
    }

    @Test
    fun finalUtterance_whileMuted_isNotSubmitted() {
        val muted = reducer.reduce(reducer.reduce(VoiceSessionState(), VoiceEvent.Start).state, VoiceEvent.Mute).state

        val transition = reducer.reduce(muted, VoiceEvent.FinalUtterance("hello"))

        assertTrue(transition.effects.none { it is VoiceEffect.SubmitTurn })
        assertEquals("", transition.state.lastUserUtterance)
    }

    @Test
    fun audioFocusLoss_pausesListeningAndSpeech() {
        val transition = reducer.reduce(speakingState(), VoiceEvent.AudioFocusLost)

        assertEquals(VoicePhase.PAUSED, transition.state.phase)
        assertTrue(transition.effects.contains(VoiceEffect.StopSpeaking))
        assertTrue(transition.effects.contains(VoiceEffect.StopListening))
    }

    @Test
    fun end_releasesAudioFocusAndTheHeadsetRoute() {
        val transition = reducer.reduce(speakingState(), VoiceEvent.End)

        assertEquals(VoicePhase.ENDED, transition.state.phase)
        assertTrue(transition.effects.contains(VoiceEffect.AbandonAudioFocus))
        assertTrue(transition.effects.contains(VoiceEffect.RestoreAudioRoute))
        assertTrue(transition.effects.contains(VoiceEffect.StopSpeaking))
    }

    @Test
    fun playbackDrained_returnsToListening() {
        val transition = reducer.reduce(speakingState(), VoiceEvent.PlaybackDrained)

        assertEquals(VoicePhase.LISTENING, transition.state.phase)
        assertEquals(listOf(VoiceEffect.StopVad, VoiceEffect.StartListening), transition.effects)
    }

    private fun speakingState(): VoiceSessionState {
        val thinking = reducer.reduce(
            reducer.reduce(VoiceSessionState(), VoiceEvent.Start).state,
            VoiceEvent.FinalUtterance("Hello")
        ).state
        return reducer.reduce(
            thinking,
            VoiceEvent.ReplyProgress("Hi there.", listOf("Hi there."), replyComplete = false)
        ).state
    }
}
