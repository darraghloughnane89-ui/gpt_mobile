package dev.chungjungsoo.gptmobile.data.voice

import dev.chungjungsoo.gptmobile.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePoliciesTest {
    @Test
    fun echoGuard_treatsRecognizedPlaybackAsEchoAndKeepsARealInterruption() {
        assertTrue(EchoGuard.isEcho("the weather is sunny", "The weather is sunny today."))
        assertFalse(EchoGuard.isEcho("stop", "The weather is sunny today."))
        assertFalse(EchoGuard.isEcho("what about tomorrow", "The weather is sunny today."))
    }

    @Test
    fun bargeInPolicy_interruptsSpeechForUserAudioAndIgnoresEchoAndMute() {
        assertTrue(
            BargeInPolicy.shouldInterrupt(
                phase = VoicePhase.SPEAKING,
                muted = false,
                heard = null,
                energy = 1f,
                recentlySpoken = "Hello"
            )
        )
        assertFalse(
            BargeInPolicy.shouldInterrupt(
                phase = VoicePhase.SPEAKING,
                muted = true,
                heard = null,
                energy = 1f,
                recentlySpoken = "Hello"
            )
        )
        assertFalse(
            BargeInPolicy.shouldInterrupt(
                phase = VoicePhase.SPEAKING,
                muted = false,
                heard = "hello there",
                energy = 0f,
                recentlySpoken = "Hello there friend"
            )
        )
        assertFalse(
            BargeInPolicy.shouldInterrupt(
                phase = VoicePhase.LISTENING,
                muted = false,
                heard = null,
                energy = 1f,
                recentlySpoken = ""
            )
        )
    }

    @Test
    fun speechEnergyGate_requiresSustainedEnergyBeforeSpeechStarts() {
        val gate = SpeechEnergyGate(threshold = 0.05f, speechWindows = 3, silenceWindows = 2)

        assertEquals(SpeechGateSignal.NONE, gate.onLevel(0.2f))
        assertEquals(SpeechGateSignal.NONE, gate.onLevel(0.2f))
        assertEquals(SpeechGateSignal.SPEECH_STARTED, gate.onLevel(0.2f))
        assertEquals(SpeechGateSignal.NONE, gate.onLevel(0.01f))
        assertEquals(SpeechGateSignal.SPEECH_ENDED, gate.onLevel(0.01f))
    }

    @Test
    fun preferredCommunicationDevice_usesBluetoothScoBeforeA2dp() {
        assertEquals(
            CommunicationDeviceType.BLUETOOTH_SCO,
            preferredCommunicationDevice(
                listOf(CommunicationDeviceType.BLUETOOTH_A2DP, CommunicationDeviceType.BLUETOOTH_SCO)
            )
        )
        assertEquals(
            CommunicationDeviceType.WIRED_HEADSET,
            preferredCommunicationDevice(listOf(CommunicationDeviceType.WIRED_HEADSET))
        )
        assertNull(preferredCommunicationDevice(emptyList()))
    }

    @Test
    fun selectOpenAiSpeechPlatform_requiresASavedKeyAndPrefersTheEnabledOpenAiHost() {
        val selected = selectOpenAiSpeechPlatform(
            listOf(
                OpenAiSpeechCandidate(true, "http://localhost:11434", "local", ClientType.OLLAMA),
                OpenAiSpeechCandidate(false, "https://api.openai.com/v1/", null, ClientType.OPENAI),
                OpenAiSpeechCandidate(true, "https://api.openai.com/v1/", "sk-test", ClientType.OPENAI)
            )
        )

        assertEquals("sk-test", selected?.token)
    }

    @Test
    fun canAcceptVoiceUtterance_rejectsBlankTextAndABusyChat() {
        assertFalse(canAcceptVoiceUtterance(isBusy = true, text = "hello"))
        assertFalse(canAcceptVoiceUtterance(isBusy = false, text = "   "))
        assertTrue(canAcceptVoiceUtterance(isBusy = false, text = "hello"))
    }

    @Test
    fun openAiSpeechRequest_escapesInputAndBuildsTheAudioUrl() {
        assertEquals(
            "https://api.openai.com/v1/audio/speech",
            openAiAudioUrl("https://api.openai.com/v1/", "audio/speech")
        )
        val json = openAiSpeechRequestJson("Say \"hi\"")
        assertTrue(json.contains("tts-1"))
        assertTrue(json.contains("Say \\\"hi\\\""))
    }

    @Test
    fun pcm16ToWav_prefixesACanonicalHeader() {
        val wav = pcm16ToWav(byteArrayOf(1, 2, 3, 4), sampleRate = 16_000)

        assertEquals(48, wav.size)
        assertEquals("RIFF", wav.decodeToString(0, 4))
        assertEquals("WAVE", wav.decodeToString(8, 12))
        assertEquals(1.toByte(), wav[44])
        assertEquals(4.toByte(), wav[47])
    }

    @Test
    fun safeSpeechError_redactsTheApiKey() {
        val message = safeSpeechError(401, "bad key sk-secret", "sk-secret")

        assertFalse(message.contains("sk-secret"))
        assertTrue(message.contains("401"))
    }

    @Test
    fun voiceLaunch_recognizesAssistAndTheExplicitExtra() {
        assertTrue(VoiceLaunch.isVoiceEntry(VoiceLaunch.ACTION_ASSIST, openVoiceExtra = false))
        assertTrue(VoiceLaunch.isVoiceEntry(VoiceLaunch.ACTION_VOICE_COMMAND, openVoiceExtra = false))
        assertTrue(VoiceLaunch.isVoiceEntry(null, openVoiceExtra = true))
        assertFalse(VoiceLaunch.isVoiceEntry("android.intent.action.MAIN", openVoiceExtra = false))
    }

    @Test
    fun normalizeRecognizerRms_staysInsideZeroAndOne() {
        assertEquals(0f, normalizeRecognizerRms(-2f))
        assertEquals(1f, normalizeRecognizerRms(10f))
    }
}
