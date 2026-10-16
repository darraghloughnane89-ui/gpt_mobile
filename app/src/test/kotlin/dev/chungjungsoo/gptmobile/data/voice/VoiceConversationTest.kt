package dev.chungjungsoo.gptmobile.data.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceConversationTest {
    @Test
    fun finalTranscript_submitsTheTurnAndSpeaksEachSentenceBeforeListeningAgain() = runTest {
        val stt = FakeStt()
        val tts = FakeTts()
        val vad = FakeVad()
        val submitted = mutableListOf<String>()
        val conversation = conversation(stt, tts, vad, submitted, scope = voiceScope())
        conversation.start()
        advanceUntilIdle()

        stt.emit(SttEvent.Partial("What is"))
        advanceUntilIdle()
        assertEquals("What is", conversation.state.value.partialTranscript)

        stt.emit(SttEvent.Final("What is the weather?"))
        advanceUntilIdle()
        assertEquals(listOf("What is the weather?"), submitted)
        assertEquals(VoicePhase.THINKING, conversation.state.value.phase)
        assertTrue(vad.started > 0)

        conversation.onChatSnapshot(
            turnCount = 1,
            assistantText = "It is sunny. More soon",
            busy = true,
            awaitingContext = false
        )
        advanceUntilIdle()
        assertEquals(listOf("It is sunny."), tts.spoken)
        assertEquals(VoicePhase.SPEAKING, conversation.state.value.phase)

        tts.finish()
        advanceUntilIdle()
        conversation.onChatSnapshot(
            turnCount = 1,
            assistantText = "It is sunny. More soon",
            busy = false,
            awaitingContext = false
        )
        advanceUntilIdle()
        assertEquals(listOf("It is sunny.", "More soon"), tts.spoken)

        tts.finish()
        advanceUntilIdle()
        assertEquals(VoicePhase.LISTENING, conversation.state.value.phase)
    }

    @Test
    fun bargeIn_whileSpeaking_stopsTtsImmediately() = runTest {
        val stt = FakeStt()
        val tts = FakeTts()
        val vad = FakeVad()
        val cancelled = mutableListOf<Int>()
        val conversation = conversation(
            stt,
            tts,
            vad,
            mutableListOf(),
            scope = voiceScope(),
            onCancel = { cancelled += 1 }
        )
        armSpeaking(conversation, stt)

        vad.speech()
        advanceUntilIdle()

        assertTrue(tts.stopCount > 0)
        assertEquals(1, cancelled.size)
        assertEquals(VoicePhase.LISTENING, conversation.state.value.phase)
        assertEquals(1, conversation.state.value.bargeInCount)
    }

    @Test
    fun echoedPartial_whileSpeaking_doesNotInterruptAndADifferentPartialDoes() = runTest {
        val stt = FakeStt()
        val tts = FakeTts()
        val conversation = conversation(stt, tts, FakeVad(), mutableListOf(), scope = voiceScope())
        armSpeaking(conversation, stt)

        stt.emit(SttEvent.Partial("It is sunny"))
        advanceUntilIdle()
        assertEquals(0, conversation.state.value.bargeInCount)

        stt.emit(SttEvent.Partial("wait stop"))
        advanceUntilIdle()
        assertEquals(1, conversation.state.value.bargeInCount)
        assertEquals(VoicePhase.LISTENING, conversation.state.value.phase)
    }

    @Test
    fun mute_preventsATranscriptFromBeingSubmitted() = runTest {
        val stt = FakeStt()
        val submitted = mutableListOf<String>()
        val conversation = conversation(stt, FakeTts(), FakeVad(), submitted, scope = voiceScope())
        conversation.start()
        advanceUntilIdle()
        conversation.mute()
        advanceUntilIdle()

        stt.emit(SttEvent.Final("hello there"))
        advanceUntilIdle()

        assertTrue(submitted.isEmpty())
        assertTrue(conversation.state.value.muted)
        assertEquals(VoicePhase.LISTENING, conversation.state.value.phase)
    }

    @Test
    fun manualInterrupt_stopsTheCurrentReply() = runTest {
        val stt = FakeStt()
        val tts = FakeTts()
        val cancelled = mutableListOf<Int>()
        val conversation = conversation(
            stt,
            tts,
            FakeVad(),
            mutableListOf(),
            scope = voiceScope(),
            onCancel = { cancelled += 1 }
        )
        armSpeaking(conversation, stt)

        conversation.interrupt()
        advanceUntilIdle()

        assertTrue(tts.stopCount > 0)
        assertEquals(1, cancelled.size)
        assertEquals(VoicePhase.LISTENING, conversation.state.value.phase)
        assertEquals(0, conversation.state.value.bargeInCount)
    }

    @Test
    fun audioFocusLoss_pausesAndEndReleasesTheRoute() = runTest {
        val focus = FakeFocus()
        val headset = FakeHeadset()
        val conversation = conversation(
            FakeStt(),
            FakeTts(),
            FakeVad(),
            mutableListOf(),
            focus,
            headset,
            scope = voiceScope()
        )
        conversation.start()
        advanceUntilIdle()
        assertTrue(focus.requests > 0)
        assertTrue(headset.engaged > 0)

        focus.lose()
        advanceUntilIdle()
        assertEquals(VoicePhase.PAUSED, conversation.state.value.phase)

        conversation.resume()
        advanceUntilIdle()
        assertEquals(VoicePhase.LISTENING, conversation.state.value.phase)

        conversation.end()
        advanceUntilIdle()
        assertEquals(VoicePhase.ENDED, conversation.state.value.phase)
        assertTrue(focus.abandoned > 0)
        assertTrue(headset.restored > 0)
    }

    private fun TestScope.voiceScope() = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.armSpeaking(conversation: VoiceConversation, stt: FakeStt) {
        conversation.start()
        advanceUntilIdle()
        stt.emit(SttEvent.Final("What is the weather?"))
        advanceUntilIdle()
        conversation.onChatSnapshot(1, "It is sunny. More soon", busy = true, awaitingContext = false)
        advanceUntilIdle()
    }

    private fun conversation(
        stt: FakeStt,
        tts: FakeTts,
        vad: FakeVad,
        submitted: MutableList<String>,
        focus: FakeFocus = FakeFocus(),
        headset: FakeHeadset = FakeHeadset(),
        scope: CoroutineScope,
        onCancel: () -> Unit = {}
    ): VoiceConversation = VoiceConversation(
        stt = stt,
        tts = tts,
        vad = vad,
        audioFocus = focus,
        headset = headset,
        scope = scope,
        submitTurn = { text ->
            submitted += text
            true
        },
        cancelTurn = onCancel
    )
}

private class FakeStt : SpeechToText {
    private val eventsFlow = MutableSharedFlow<SttEvent>(extraBufferCapacity = 8)
    override val events: Flow<SttEvent> = eventsFlow.asSharedFlow()
    override suspend fun start() = Unit
    override suspend fun stop() = Unit

    fun emit(event: SttEvent) {
        eventsFlow.tryEmit(event)
    }
}

private class FakeTts : TextToSpeechEngine {
    val spoken = mutableListOf<String>()
    var stopCount = 0
    private var gate = CompletableDeferred<Unit>()

    override suspend fun speak(utteranceId: String, text: String) {
        spoken += text
        gate = CompletableDeferred()
        gate.await()
    }

    override suspend fun stop() {
        stopCount += 1
        if (!gate.isCompleted) gate.complete(Unit)
    }

    fun finish() {
        if (!gate.isCompleted) gate.complete(Unit)
    }
}

private class FakeVad : VoiceActivityDetector {
    private val speech = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val levels = MutableSharedFlow<Float>(extraBufferCapacity = 1)
    override val speechStarted: Flow<Unit> = speech.asSharedFlow()
    override val level: Flow<Float> = levels
    var started = 0

    override suspend fun start() {
        started += 1
    }

    override suspend fun stop() = Unit

    fun speech() {
        speech.tryEmit(Unit)
    }
}

private class FakeFocus : AudioFocusGateway {
    private val lost = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val focusLost: Flow<Unit> = lost
    var requests = 0
    var abandoned = 0

    override fun requestPlayback(): Boolean {
        requests += 1
        return true
    }

    override fun abandon() {
        abandoned += 1
    }

    fun lose() {
        lost.tryEmit(Unit)
    }
}

private class FakeHeadset : HeadsetRouter {
    private val connected = MutableStateFlow(false)
    override val usingHeadset = connected.asStateFlow()
    var engaged = 0
    var restored = 0

    override fun engage() {
        engaged += 1
    }

    override fun restore() {
        restored += 1
    }
}
