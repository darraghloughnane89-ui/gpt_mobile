package dev.chungjungsoo.gptmobile.data.voice

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiMoSpeechProtocolTest {
    @Test
    fun `completions url is the europe token plan chat endpoint`() {
        assertEquals(
            "https://token-plan-ams.xiaomimimo.com/v1/chat/completions",
            MiMoSpeechProtocol.completionsUrl()
        )
    }

    @Test
    fun `asr body uses the documented model and data uri`() {
        val body = MiMoSpeechProtocol.transcriptionBody("audio/wav", "AAAA", "en")
        assertTrue(body.contains("\"model\":\"${MiMoSpeechModels.ASR}\""))
        assertTrue(body.contains("\"type\":\"input_audio\""))
        assertTrue(body.contains("data:audio/wav;base64,AAAA"))
        assertTrue(body.contains("\"language\":\"en\""))
    }

    @Test
    fun `streaming tts body uses pcm16 and the preset voice`() {
        val body = MiMoSpeechProtocol.synthesisBody("Hello", "Chloe", stream = true)
        assertTrue(body.contains("\"model\":\"${MiMoSpeechModels.TTS}\""))
        assertTrue(body.contains("\"stream\":true"))
        assertTrue(body.contains("\"role\":\"assistant\""))
        assertTrue(body.contains("\"format\":\"pcm16\""))
        assertTrue(body.contains("\"voice\":\"Chloe\""))
        assertFalse(body.contains("\"role\":\"user\""))
    }

    @Test
    fun `unknown voice falls back to the documented default`() {
        val body = MiMoSpeechProtocol.synthesisBody("Hello", "not-a-voice", stream = false)
        assertTrue(body.contains("\"voice\":\"${MiMoSpeechModels.DEFAULT_VOICE}\""))
        assertTrue(body.contains("\"format\":\"wav\""))
        assertTrue(body.contains("\"stream\":false"))
    }

    @Test
    fun `transcript and sse audio chunks follow the chat completion shape`() {
        assertEquals(
            "hello there",
            MiMoSpeechProtocol.transcript("""{"choices":[{"message":{"content":"hello there"}}]}""")
        )
        val chunk = MiMoSpeechProtocol.audioChunks(
            """data: {"choices":[{"delta":{"audio":{"data":"AQI="}}}]}"""
        )
        assertTrue(chunk != null && chunk.contentEquals(Base64.getDecoder().decode("AQI=")))
        assertNull(MiMoSpeechProtocol.audioChunks("data: [DONE]"))
    }

    @Test
    fun `speech errors are redacted and do not keep the api key`() {
        val message = MiMoSpeechProtocol.redact(401, """{"error":{"message":"bad tp-secret-key"}}""", "tp-secret-key")
        assertTrue(message.contains("***"))
        assertFalse(message.contains("tp-secret-key"))
        try {
            MiMoSpeechProtocol.audioChunks("""data: {"error":{"message":"tp-secret-key rejected"}}""")
            throw AssertionError("expected a speech error")
        } catch (error: MiMoSpeechException) {
            assertTrue(error.message!!.contains("rejected"))
        }
    }

    @Test
    fun `key selection prefers the vault entry then an europe token plan platform`() {
        val platform = MiMoKeyCandidate(true, "https://token-plan-ams.xiaomimimo.com/v1/", "tp-platform")
        val other = MiMoKeyCandidate(true, "https://api.openai.com/v1", "sk-other")
        assertEquals("tp-dedicated", MiMoSpeechProtocol.selectKey(" tp-dedicated ", listOf(platform)))
        assertEquals("tp-platform", MiMoSpeechProtocol.selectKey(null, listOf(other, platform)))
        assertNull(MiMoSpeechProtocol.selectKey("  ", listOf(other)))
        assertFalse(platform.toString().contains("tp-platform"))
        assertEquals("1234", MiMoSpeechProtocol.keyPreview("tp-secret-1234"))
        assertEquals("", MiMoSpeechProtocol.keyPreview("short"))
    }

    @Test
    fun `enabled europe platform wins over a disabled one`() {
        val disabled = MiMoKeyCandidate(false, MiMoSpeechModels.BASE_URL, "tp-disabled")
        val enabled = MiMoKeyCandidate(true, MiMoSpeechModels.BASE_URL, "tp-enabled")
        assertEquals("tp-enabled", MiMoSpeechProtocol.selectKey(null, listOf(disabled, enabled)))
        assertEquals("tp-disabled", MiMoSpeechProtocol.selectKey(null, listOf(disabled)))
    }

    @Test
    fun `energy gate and pcm framing`() {
        val gate = SpeechEnergyGate()
        assertEquals(SpeechGateSignal.NONE, gate.onLevel(0.05f))
        assertEquals(SpeechGateSignal.NONE, gate.onLevel(0.05f))
        assertEquals(SpeechGateSignal.SPEECH_STARTED, gate.onLevel(0.05f))
        repeat(7) { assertEquals(SpeechGateSignal.NONE, gate.onLevel(0f)) }
        assertEquals(SpeechGateSignal.SPEECH_ENDED, gate.onLevel(0f))

        val framer = Pcm16Framer()
        assertEquals(0, framer.frame(byteArrayOf(1)).size)
        assertTrue(framer.frame(byteArrayOf(2, 3)).contentEquals(byteArrayOf(1, 2)))
        val wav = pcm16ToWav(byteArrayOf(1, 2), 16_000)
        assertEquals('R'.code.toByte(), wav[0])
        assertEquals('W'.code.toByte(), wav[8])
        assertEquals(46, wav.size)
    }

    @Test
    fun `backend selection falls back without a key`() {
        assertEquals(VoiceSpeechBackend.DEVICE, selectVoiceBackend(useMiMo = false, hasKey = true))
        assertEquals(VoiceSpeechBackend.DEVICE, selectVoiceBackend(useMiMo = true, hasKey = false))
        assertEquals(VoiceSpeechBackend.MIMO, selectVoiceBackend(useMiMo = true, hasKey = true))
        assertEquals(VoiceBackendStatus.DEVICE_MISSING_KEY, voiceBackendStatus(true, false))
        assertEquals(VoiceSessionAdoption.ROUTE, "voice_session")
    }
}
