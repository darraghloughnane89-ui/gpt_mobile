package dev.chungjungsoo.gptmobile.data.voice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPlaybackTest {
    @Test
    fun `chunks play in order until the stream ends`() = runTest(UnconfinedTestDispatcher()) {
        val sink = RecordingSink()
        val result = StreamingPlayback.play(
            chunks = flow {
                emit(byteArrayOf(1, 2))
                emit(byteArrayOf(3, 4))
            },
            sink = sink,
            sampleRateHz = 24_000,
            interrupt = flow { awaitCancellation() }
        )
        assertEquals(PlaybackResult.COMPLETED, result)
        assertEquals(2, sink.writes.size)
        assertTrue(sink.writes[0].contentEquals(byteArrayOf(1, 2)))
        assertTrue(sink.writes[1].contentEquals(byteArrayOf(3, 4)))
        assertTrue(sink.stopped)
        assertEquals(24_000, sink.sampleRate)
    }

    @Test
    fun `barge-in stops playback before later chunks`() = runTest {
        val interrupt = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val sink = object : RecordingSink() {
            override fun write(pcm: ByteArray) {
                super.write(pcm)
                interrupt.tryEmit(Unit)
            }
        }
        val result = StreamingPlayback.play(
            chunks = flow {
                emit(byteArrayOf(1, 2))
                yield()
                emit(byteArrayOf(3, 4))
            },
            sink = sink,
            sampleRateHz = 24_000,
            interrupt = interrupt
        )
        assertEquals(PlaybackResult.INTERRUPTED, result)
        assertEquals(1, sink.writes.size)
        assertTrue(sink.stopped)
    }

    @Test
    fun `stop cancels an open playback`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val sink = RecordingSink()
        val speaker = MiMoSpeechSpeaker(
            transport = object : MiMoSpeechTransport {
                override suspend fun transcribe(apiKey: String, wav: ByteArray, language: String) = ""

                override fun synthesize(apiKey: String, text: String, voice: String): Flow<ByteArray> = flow {
                    emit(byteArrayOf(1, 2))
                    started.complete(Unit)
                    awaitCancellation()
                }
            },
            keys = MemoryVoiceKeyStore("tp-secret-key-1234"),
            preferences = MemoryVoicePreferences(VoiceSpeechSettings(useMiMo = true, voiceId = "Mia")),
            sink = sink,
            bargeIn = BargeInEvents { flow { awaitCancellation() } }
        )
        val job = launch(Dispatchers.IO) { speaker.speak("preview", "Hello") }
        withTimeout(5_000) { started.await() }
        speaker.stop()
        withTimeout(5_000) { job.join() }
        assertTrue(sink.stopped)
    }
}

open class RecordingSink : PcmSink {
    val writes = mutableListOf<ByteArray>()
    var stopped = false
    var sampleRate = 0

    override fun open(sampleRateHz: Int) {
        sampleRate = sampleRateHz
        stopped = false
    }

    override fun write(pcm: ByteArray) {
        writes += pcm.copyOf()
    }

    override fun stop() {
        stopped = true
    }
}
