package dev.chungjungsoo.gptmobile.data.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

@SuppressLint("MissingPermission")
class AndroidBargeIn(
    private val microphone: MicrophoneAccess
) : BargeInEvents {
    override fun speech(): Flow<Unit> = callbackFlow {
        if (!microphone.granted()) {
            awaitClose { }
            return@callbackFlow
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            awaitClose { }
            return@callbackFlow
        }
        val recorder = openRecorder(minBuffer)
        if (recorder == null) {
            awaitClose { }
            return@callbackFlow
        }
        val canceler = if (AcousticEchoCanceler.isAvailable()) {
            runCatching { AcousticEchoCanceler.create(recorder.audioSessionId) }.getOrNull()
        } else {
            null
        }
        canceler?.enabled = true
        val gate = SpeechEnergyGate()
        val buffer = ShortArray(minBuffer)
        try {
            recorder.startRecording()
            while (isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                yield()
                if (read <= 0) continue
                if (gate.onLevel(pcmRms(buffer, read)) == SpeechGateSignal.SPEECH_STARTED) {
                    trySend(Unit)
                    break
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Playback continues when the microphone cannot be opened.
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            runCatching { canceler?.release() }
        }
        awaitClose { }
    }

    private fun openRecorder(minBuffer: Int): AudioRecord? {
        val recorder = runCatching {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .build()
        }.getOrNull() ?: return null
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        return recorder
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
