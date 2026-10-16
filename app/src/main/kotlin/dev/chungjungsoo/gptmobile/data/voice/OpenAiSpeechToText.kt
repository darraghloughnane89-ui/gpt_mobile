package dev.chungjungsoo.gptmobile.data.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class OpenAiSpeechToText(
    private val context: Context,
    private val credentials: VoiceCredentialLookup,
    private val client: OpenAiAudioClient
) : SpeechToText {
    private val eventsFlow = MutableSharedFlow<SttEvent>(extraBufferCapacity = 16)
    private val levelsFlow = MutableSharedFlow<Float>(extraBufferCapacity = 8)
    override val events = eventsFlow.asSharedFlow()
    override val levels = levelsFlow.asSharedFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile
    private var running = false

    @SuppressLint("MissingPermission")
    override suspend fun start() {
        stop()
        running = true
        job = scope.launch {
            runCatching { captureUntilStopped() }
                .onFailure { error ->
                    Log.w(TAG, "OpenAI transcription stopped", error)
                    eventsFlow.tryEmit(SttEvent.Failure(error.message ?: "OpenAI transcription failed.", recoverable = true))
                }
        }
    }

    override suspend fun stop() {
        running = false
        job?.cancel()
        job = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun captureUntilStopped() {
        val credential = credentials.openAiCredential()
        if (credential == null) {
            eventsFlow.tryEmit(SttEvent.Failure("Add an OpenAI API key to use OpenAI transcription.", recoverable = false))
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            eventsFlow.tryEmit(SttEvent.Failure("The microphone could not be opened.", recoverable = false))
            return
        }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            eventsFlow.tryEmit(SttEvent.Failure("The microphone could not be opened.", recoverable = false))
            return
        }
        val gate = SpeechEnergyGate()
        val buffer = ShortArray(minBuffer)
        val utterance = ByteArrayOutputStream()
        var speechActive = false
        var utteranceSamples = 0
        try {
            recorder.startRecording()
            while (running && coroutineContext.isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val normalized = pcmRms(buffer, read)
                levelsFlow.tryEmit(normalized.coerceIn(0f, 1f))
                when (gate.onLevel(normalized)) {
                    SpeechGateSignal.SPEECH_STARTED -> {
                        speechActive = true
                        eventsFlow.tryEmit(SttEvent.Partial(""))
                    }
                    SpeechGateSignal.SPEECH_ENDED -> {
                        if (speechActive && utterance.size() > 0) {
                            transcribe(credential, utterance.toByteArray())
                        }
                        speechActive = false
                        utterance.reset()
                        utteranceSamples = 0
                    }
                    SpeechGateSignal.NONE -> Unit
                }
                if (speechActive) {
                    utterance.write(shortsToLittleEndian(buffer, read))
                    utteranceSamples += read
                    if (utteranceSamples >= SAMPLE_RATE * MAX_UTTERANCE_SECONDS) {
                        transcribe(credential, utterance.toByteArray())
                        speechActive = false
                        utterance.reset()
                        utteranceSamples = 0
                        gate.reset()
                    }
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private suspend fun transcribe(credential: VoiceProviderCredential, pcm: ByteArray) {
        if (pcm.isEmpty()) return
        val text = client.transcribe(credential, pcm16ToWav(pcm, SAMPLE_RATE)).trim()
        if (text.isNotBlank()) {
            eventsFlow.tryEmit(SttEvent.Final(text))
        }
    }

    private companion object {
        const val TAG = "VoiceOpenAiStt"
        const val SAMPLE_RATE = 16_000
        const val MAX_UTTERANCE_SECONDS = 20
    }
}

internal fun shortsToLittleEndian(samples: ShortArray, count: Int): ByteArray {
    val bytes = ByteArray(count * 2)
    for (index in 0 until count) {
        val value = samples[index].toInt()
        bytes[index * 2] = (value and 0xff).toByte()
        bytes[index * 2 + 1] = (value shr 8 and 0xff).toByte()
    }
    return bytes
}
