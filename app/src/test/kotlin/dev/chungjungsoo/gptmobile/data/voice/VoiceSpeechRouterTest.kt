package dev.chungjungsoo.gptmobile.data.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSpeechRouterTest {
    @Test
    fun `recognition uses on-device speech unless MiMo is selected and a key exists`() = runTest {
        val scope = testScope()
        val device = FakeRecognizer()
        val mimo = FakeRecognizer()
        val router = router(scope, device, mimo, useMiMo = false, key = "tp-secret-key-1234")
        try {
            router.recognizer.start()
            assertEquals(1, device.started)
            assertEquals(0, mimo.started)
            assertEquals(VoiceSpeechBackend.DEVICE, router.activeBackend.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `recognition uses MiMo when the toggle and key are set`() = runTest {
        val scope = testScope()
        val device = FakeRecognizer()
        val mimo = FakeRecognizer()
        val router = router(scope, device, mimo, useMiMo = true, key = "tp-secret-key-1234")
        val heard = mutableListOf<String>()
        val collecting = scope.launch {
            router.recognizer.events.collect { event ->
                if (event is VoiceTranscript.Final) heard += event.text
            }
        }
        try {
            router.recognizer.start()
            mimo.emit(VoiceTranscript.Final("hello"))
            assertEquals(1, mimo.started)
            assertEquals(0, device.started)
            assertEquals(listOf("hello"), heard)
            assertEquals(VoiceSpeechBackend.MIMO, router.activeBackend.value)
        } finally {
            collecting.cancel()
            scope.cancel()
        }
    }

    @Test
    fun `a missing key falls back to on-device recognition`() = runTest {
        val scope = testScope()
        val device = FakeRecognizer()
        val mimo = FakeRecognizer()
        val router = router(scope, device, mimo, useMiMo = true, key = null)
        try {
            router.recognizer.start()
            assertEquals(1, device.started)
            assertEquals(0, mimo.started)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `recognition falls back when MiMo fails to start`() = runTest {
        val scope = testScope()
        val device = FakeRecognizer()
        val mimo = FakeRecognizer(startError = IllegalStateException("unavailable"))
        val router = router(scope, device, mimo, useMiMo = true, key = "tp-secret-key-1234")
        try {
            router.recognizer.start()
            assertEquals(1, mimo.started)
            assertEquals(1, device.started)
            assertEquals(VoiceSpeechBackend.DEVICE, router.activeBackend.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `spoken replies fall back to on-device speech and barge-in does not replay them`() = runTest {
        val scope = testScope()
        val deviceSpeaker = FakeSpeaker()
        val mimoSpeaker = FakeSpeaker(fail = IllegalStateException("no audio"))
        val router = router(
            scope = scope,
            deviceSpeaker = deviceSpeaker,
            mimoSpeaker = mimoSpeaker,
            useMiMo = true,
            key = "tp-secret-key-1234"
        )
        router.speaker.speak("reply", "Hello")
        assertEquals(listOf("Hello"), deviceSpeaker.spoken)
        assertEquals(listOf("Hello"), mimoSpeaker.spoken)

        val cancelling = FakeSpeaker(fail = CancellationException("barge-in"))
        val quietDevice = FakeSpeaker()
        val bargeRouter = router(
            scope = scope,
            deviceSpeaker = quietDevice,
            mimoSpeaker = cancelling,
            useMiMo = true,
            key = "tp-secret-key-1234"
        )
        var cancelled = false
        try {
            bargeRouter.speaker.speak("reply", "Stop")
        } catch (error: CancellationException) {
            cancelled = true
            assertTrue(error.message == "barge-in")
        }
        assertTrue(cancelled)
        assertTrue(quietDevice.spoken.isEmpty())
        scope.cancel()
    }

    @Test
    fun `blank replies are not spoken`() = runTest {
        val scope = testScope()
        val deviceSpeaker = FakeSpeaker()
        val router = router(scope, deviceSpeaker = deviceSpeaker, useMiMo = false, key = null)
        router.speaker.speak("reply", "  ")
        assertTrue(deviceSpeaker.spoken.isEmpty())
        scope.cancel()
    }

    private fun testScope() = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    private fun router(
        scope: CoroutineScope,
        device: FakeRecognizer = FakeRecognizer(),
        mimo: FakeRecognizer = FakeRecognizer(),
        deviceSpeaker: FakeSpeaker = FakeSpeaker(),
        mimoSpeaker: FakeSpeaker = FakeSpeaker(),
        useMiMo: Boolean,
        key: String?
    ) = VoiceSpeechRouter(
        deviceRecognizer = device,
        mimoRecognizer = mimo,
        deviceSpeaker = deviceSpeaker,
        mimoSpeaker = mimoSpeaker,
        preferences = MemoryVoicePreferences(VoiceSpeechSettings(useMiMo = useMiMo)),
        keys = MemoryVoiceKeyStore(key),
        parentScope = scope
    )
}

private class FakeRecognizer(
    private val startError: Exception? = null
) : VoiceRecognizer {
    var started = 0
    private val eventsFlow = MutableSharedFlow<VoiceTranscript>(extraBufferCapacity = 4)
    override val events = eventsFlow
    override val levels = emptyFlow<Float>()

    override suspend fun start() {
        started += 1
        startError?.let { throw it }
    }

    override suspend fun stop() = Unit

    suspend fun emit(event: VoiceTranscript) {
        eventsFlow.emit(event)
    }
}

private class FakeSpeaker(
    private val fail: Exception? = null
) : VoiceSpeaker {
    val spoken = mutableListOf<String>()

    override suspend fun speak(utteranceId: String, text: String) {
        spoken += text
        fail?.let { throw it }
    }

    override suspend fun stop() = Unit
}
