package dev.chungjungsoo.gptmobile.data.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

interface PcmSink {
    fun open(sampleRateHz: Int)

    fun write(pcm: ByteArray)

    fun stop()
}

enum class PlaybackResult {
    COMPLETED,
    INTERRUPTED,
    EMPTY
}

object StreamingPlayback {
    suspend fun play(
        chunks: Flow<ByteArray>,
        sink: PcmSink,
        sampleRateHz: Int,
        interrupt: Flow<Unit>
    ): PlaybackResult = coroutineScope {
        sink.open(sampleRateHz)
        val framer = Pcm16Framer()
        val outcome = CompletableDeferred<PlaybackResult>()
        val writing = JobSlot()
        val watching = launch {
            try {
                interrupt.first()
                if (!outcome.isCompleted) outcome.complete(PlaybackResult.INTERRUPTED)
                sink.stop()
                writing.job?.cancel()
            } catch (error: CancellationException) {
                // Playback finished, or the caller stopped the session.
            } catch (_: NoSuchElementException) {
                // The interrupt source closed without hearing speech.
            }
        }
        yield()
        writing.job = launch {
            if (outcome.isCompleted) return@launch
            var wrote = false
            try {
                chunks.collect { chunk ->
                    if (outcome.isCompleted) return@collect
                    val framed = framer.frame(chunk)
                    if (framed.isNotEmpty()) {
                        wrote = true
                        sink.write(framed)
                    }
                }
                if (!outcome.isCompleted) {
                    outcome.complete(if (wrote) PlaybackResult.COMPLETED else PlaybackResult.EMPTY)
                }
            } catch (error: CancellationException) {
                if (!outcome.isCompleted) outcome.complete(PlaybackResult.INTERRUPTED)
            } catch (error: Exception) {
                if (!outcome.isCompleted) outcome.completeExceptionally(error)
            } finally {
                sink.stop()
            }
        }
        try {
            outcome.await()
        } finally {
            watching.cancel()
            writing.job?.cancel()
        }
    }
}

private class JobSlot {
    var job: Job? = null
}
