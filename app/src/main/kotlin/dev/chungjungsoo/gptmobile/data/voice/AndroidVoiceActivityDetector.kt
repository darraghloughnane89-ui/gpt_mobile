package dev.chungjungsoo.gptmobile.data.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidVoiceActivityDetector : VoiceActivityDetector {
    private val speechFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val levelFlow = MutableSharedFlow<Float>(extraBufferCapacity = 8)
    override val speechStarted = speechFlow.asSharedFlow()
    override val level = levelFlow.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile
    private var running = false

    @SuppressLint("MissingPermission")
    override suspend fun start() {
        stop()
        running = true
        job = scope.launch {
            runCatching { capture() }
                .onFailure { error -> Log.w(TAG, "Voice activity detector stopped", error) }
        }
    }

    override suspend fun stop() {
        running = false
        job?.cancel()
        job = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun capture() {
        val sampleRate = SAMPLE_RATE
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channel, encoding)
        if (minBuffer <= 0) return
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channel,
            encoding,
            minBuffer * 2
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }
        val echoCanceler = if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(recorder.audioSessionId)?.also { it.enabled = true }
        } else {
            null
        }
        val noiseSuppressor = if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(recorder.audioSessionId)?.also { it.enabled = true }
        } else {
            null
        }
        val gate = SpeechEnergyGate()
        val buffer = ShortArray(minBuffer)
        try {
            recorder.startRecording()
            while (running && coroutineContext.isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val normalized = pcmRms(buffer, read)
                levelFlow.tryEmit(normalized.coerceIn(0f, 1f))
                if (gate.onLevel(normalized) == SpeechGateSignal.SPEECH_STARTED) {
                    speechFlow.tryEmit(Unit)
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            echoCanceler?.release()
            noiseSuppressor?.release()
        }
    }

    private companion object {
        const val TAG = "VoiceVad"
        const val SAMPLE_RATE = 16_000
    }
}
