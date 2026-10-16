package dev.chungjungsoo.gptmobile.data.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandsFreeVoiceTest {
    @Test
    fun `voice session route is the adoption path`() {
        assertEquals("voice_session", VoiceSessionAdoption.ROUTE)
        assertEquals(
            "voice_session?chatRoomId=4&enabled=alpha,beta",
            VoiceSessionAdoption.destination(4, "alpha,beta")
        )
    }

    @Test
    fun `wake phrase matches hey robin and ignores nearby phrases`() {
        assertTrue(WakePhrase.matches("Hey Robin"))
        assertTrue(WakePhrase.matches("hey, robin!"))
        assertTrue(WakePhrase.matches("Hey Robin what's the weather"))
        assertFalse(WakePhrase.matches("hey google"))
        assertFalse(WakePhrase.matches("hello robin"))
        assertFalse(WakePhrase.matches(""))
    }

    @Test
    fun `licence is the open wake word contract and not picovoice`() {
        val licence = WakeEngineLicence.OPEN_WAKE_WORD
        assertEquals("Apache-2.0", licence.spdx)
        assertTrue(licence.url.contains("openWakeWord"))
        assertTrue(licence.note.contains("No Picovoice"))
        assertFalse(licence.note.contains("access key", ignoreCase = true))
    }

    @Test
    fun `hey robin audio scores above a different phrase and silence`() {
        val engine = OpenWakeWordEngine()
        val hey = pad(synthesize(heyRobinRecipe(speed = 1.04f, pitchScale = 1.02f)))
        val other = pad(synthesize(otherPhraseRecipe(speed = 1.04f, pitchScale = 1.02f)))
        val silence = ShortArray(16_000)
        val heyScore = engine.scorePcm(hey)
        val otherScore = engine.scorePcm(other)
        val silenceScore = engine.scorePcm(silence)
        assertTrue("hey=$heyScore other=$otherScore silence=$silenceScore", heyScore >= OpenWakeWordEngine.THRESHOLD)
        assertTrue("hey=$heyScore other=$otherScore", heyScore > otherScore + 0.05f)
        assertTrue("other=$otherScore", otherScore < OpenWakeWordEngine.THRESHOLD)
        assertTrue("silence=$silenceScore", silenceScore < OpenWakeWordEngine.THRESHOLD)
    }

    @Test
    fun `streaming hey robin latches once`() {
        val engine = OpenWakeWordEngine()
        val audio = pad(synthesize(heyRobinRecipe(1.04f, 1.02f)))
        var detections = 0
        var offset = 0
        while (offset < audio.size) {
            val end = (offset + OpenWakeWordEngine.FRAME_SAMPLES).coerceAtMost(audio.size)
            val score = engine.accept(audio.copyOfRange(offset, end))
            if (score.detected) detections += 1
            offset = end
        }
        assertEquals(1, detections)
    }

    @Test
    fun `onnx runner adoption uses the supplied score`() {
        val engine = OnnxAdoptedWakeEngine(runner = { features -> if (features.isNotEmpty()) 0.9f else 0f })
        val detected = engine.accept(ShortArray(1_600))
        assertTrue(detected.detected)
        assertEquals(0.9f, detected.similarity, 0.001f)
    }

    @Test
    fun `capture stays blocked until the notification is posted`() {
        val gate = NotificationCaptureGate()
        val engine = CountingEngine()
        val processor = WakeFrameProcessor(gate, engine)
        assertFalse(ForegroundCapture.begin(notificationPosted = false, gate))
        assertEquals(WakeFrameKind.BLOCKED, processor.onPcm(ShortArray(8), 1_000).kind)
        assertEquals(0, engine.calls)
        assertTrue(ForegroundCapture.begin(notificationPosted = true, gate))
        engine.next = WakeScore(0.95f, true)
        assertEquals(WakeFrameKind.DETECTED, processor.onPcm(ShortArray(8), 2_000).kind)
        assertEquals(WakeFrameKind.LISTENING, processor.onPcm(ShortArray(8), 2_500).kind)
        ForegroundCapture.end(gate)
        assertEquals(WakeFrameKind.BLOCKED, processor.onPcm(ShortArray(8), 5_000).kind)
    }

    @Test
    fun `gated recognizer does not start the delegate without a notification`() = runBlocking {
        val gate = NotificationCaptureGate()
        val delegate = FakeRecognizer()
        val gated = GatedVoiceRecognizer(gate, delegate)
        val events = mutableListOf<VoiceTranscript>()
        val collector = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launchCollect(gated, events)
        gated.start()
        assertEquals(0, delegate.starts)
        assertTrue(events.last() is VoiceTranscript.Failure)
        ForegroundCapture.begin(notificationPosted = true, gate)
        gated.start()
        assertEquals(1, delegate.starts)
        collector.cancel()
    }

    @Test
    fun `battery stop keeps a charging phone awake and stops a low battery`() {
        val low = BatteryStopPolicy.snapshot(
            level = 10,
            scale = 100,
            status = 3,
            systemLow = false
        )
        val charging = BatteryStopPolicy.snapshot(
            level = 10,
            scale = 100,
            status = android.os.BatteryManager.BATTERY_STATUS_CHARGING,
            systemLow = true
        )
        val healthy = BatteryStopPolicy.snapshot(level = 40, scale = 100, status = 3, systemLow = false)
        assertTrue(BatteryStopPolicy.shouldStop(low))
        assertFalse(BatteryStopPolicy.shouldStop(charging))
        assertFalse(BatteryStopPolicy.shouldStop(healthy))
        assertTrue(BatteryStopPolicy.shouldStop(healthy.copy(systemLow = true)))
    }

    @Test
    fun `continuous conversation speaks then listens again`() {
        val reducer = HandsFreeReducer()
        var state = HandsFreeState()
        val started = reducer.reduce(state, HandsFreeEvent.Start)
        state = started.state
        assertEquals(HandsFreePhase.LISTENING, state.phase)
        assertTrue(started.effects.contains(HandsFreeEffect.StartListening))

        val asked = reducer.reduce(state, HandsFreeEvent.FinalUtterance("How far is the next exit?"))
        state = asked.state
        assertEquals(HandsFreePhase.THINKING, state.phase)
        assertTrue(asked.effects.contains(HandsFreeEffect.SubmitTurn("How far is the next exit?")))

        val spoken = reducer.reduce(
            state,
            HandsFreeEvent.ReplyProgress("About ten minutes.", listOf("About ten minutes."), false)
        )
        state = spoken.state
        assertEquals(HandsFreePhase.SPEAKING, state.phase)
        assertTrue(spoken.effects.contains(HandsFreeEffect.Speak("About ten minutes.")))

        val again = reducer.reduce(state, HandsFreeEvent.PlaybackDrained)
        assertEquals(HandsFreePhase.LISTENING, again.state.phase)
        assertTrue(again.effects.contains(HandsFreeEffect.StartListening))

        val interrupted = reducer.reduce(state, HandsFreeEvent.Interrupt)
        assertEquals(HandsFreePhase.LISTENING, interrupted.state.phase)
        assertTrue(interrupted.effects.contains(HandsFreeEffect.StopSpeaking))

        val ended = reducer.reduce(again.state, HandsFreeEvent.End)
        assertEquals(HandsFreePhase.ENDED, ended.state.phase)
        assertTrue(ended.effects.contains(HandsFreeEffect.ReleaseCapture))
        assertFalse(ended.effects.contains(HandsFreeEffect.StartListening))
    }

    @Test
    fun `echo and busy turns are not submitted`() {
        val reducer = HandsFreeReducer()
        val listening = reducer.reduce(HandsFreeState(), HandsFreeEvent.Start).state
            .copy(recentlySpoken = "About ten minutes")
        val echo = reducer.reduce(listening, HandsFreeEvent.FinalUtterance("about ten minutes"))
        assertEquals(HandsFreePhase.LISTENING, echo.state.phase)
        assertTrue(echo.effects.isEmpty())
        assertFalse(canAcceptVoiceUtterance(isBusy = true, text = "hello"))
        assertFalse(canAcceptVoiceUtterance(isBusy = false, text = "  "))
        assertTrue(canAcceptVoiceUtterance(isBusy = false, text = "hello"))
    }

    @Test
    fun `battery and missing notification end the session without recording`() {
        val reducer = HandsFreeReducer()
        val listening = reducer.reduce(HandsFreeState(), HandsFreeEvent.Start).state
        val battery = reducer.reduce(listening, HandsFreeEvent.BatteryStop)
        assertEquals(HandsFreePhase.ENDED, battery.state.phase)
        assertEquals(HandsFreeReducer.BATTERY_STATUS, battery.state.statusMessage)
        assertTrue(battery.effects.contains(HandsFreeEffect.StopListening))
        val blocked = reducer.reduce(listening, HandsFreeEvent.CaptureBlocked)
        assertEquals(HandsFreePhase.PAUSED, blocked.state.phase)
        assertTrue(blocked.effects.contains(HandsFreeEffect.StopListening))
    }

    @Test
    fun `replies are split into speakable sentences`() {
        val splitter = ReplySentenceSplitter()
        val first = splitter.consume("The next exit is close. The one after is far", complete = false)
        assertEquals(listOf("The next exit is close."), first)
        val rest = splitter.consume("The next exit is close. The one after is far.", complete = true)
        assertEquals(listOf("The one after is far."), rest)
        val markdown = speakableReply("See [the map](https://example.com) and **go**.", complete = true)
        assertFalse(markdown.contains("http"))
        assertFalse(markdown.contains("**"))
        assertTrue(markdown.contains("the map"))
    }

    private fun pad(samples: ShortArray): ShortArray {
        val silence = ShortArray(3_200)
        return silence + samples
    }

    private class CountingEngine : WakePhraseEngine {
        var calls = 0
        var next = WakeScore(0f, false)
        override val phrase = WakePhrase.SPOKEN
        override val licence = WakeEngineLicence.OPEN_WAKE_WORD
        override fun reset() = Unit
        override fun accept(pcm16: ShortArray): WakeScore {
            calls += 1
            return next
        }
    }

    private class FakeRecognizer : VoiceRecognizer {
        var starts = 0
        override val events = MutableSharedFlow<VoiceTranscript>()
        override val levels: Flow<Float> = emptyFlow()
        override suspend fun start() {
            starts += 1
        }
        override suspend fun stop() = Unit
    }
}

private fun CoroutineScope.launchCollect(
    gated: GatedVoiceRecognizer,
    events: MutableList<VoiceTranscript>
): Job = launch {
    gated.events.collect { events.add(it) }
}
