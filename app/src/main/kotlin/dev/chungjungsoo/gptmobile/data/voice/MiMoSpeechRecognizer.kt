package dev.chungjungsoo.gptmobile.data.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class MiMoSpeechRecognizer(
    private val transport: MiMoSpeechTransport,
    private val keys: VoiceSpeechKeyStore,
    private val microphone: MicrophoneAccess
) : VoiceRecognizer {
    private val eventsFlow = MutableSharedFlow<VoiceTranscript>(extraBufferCapacity = 16)
    private val levelsFlow = MutableSharedFlow<Float>(extraBufferCapacity = 8)
    override val events = eventsFlow.asSharedFlow()
    override val levels = levelsFlow.asSharedFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile
    private var running = false

    override suspend fun start() {
        stop()
        if (!microphone.granted()) {
            eventsFlow.tryEmit(
                VoiceTranscript.Failure(
                    message = "Microphone permission is required for MiMo speech recognition.",
                    recoverable = true
                )
            )
            return
        }
        running = true
        job = scope.launch {
            runCatching { captureUntilStopped() }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) return@onFailure
                    eventsFlow.tryEmit(
                        VoiceTranscript.Failure(
                            message = error.message ?: "MiMo speech recognition failed.",
                            recoverable = true
                        )
                    )
                }
        }
    }

    override suspend fun stop() {
        running = false
        val active = job
        job = null
        active?.cancelAndJoin()
    }

    private suspend fun captureUntilStopped() {
        val apiKey = keys.readKey()
        if (apiKey.isNullOrBlank()) {
            eventsFlow.tryEmit(
                VoiceTranscript.Failure(
                    message = "Add a MiMo key to use MiMo speech recognition.",
                    recoverable = false
                )
            )
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            MiMoSpeechModels.ASR_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            eventsFlow.tryEmit(VoiceTranscript.Failure("The microphone could not be opened.", recoverable = false))
            return
        }
        val recorder = openRecorder(minBuffer) ?: run {
            eventsFlow.tryEmit(VoiceTranscript.Failure("The microphone could not be opened.", recoverable = false))
            return
        }
        val gate = SpeechEnergyGate()
        val buffer = ShortArray(minBuffer)
        val utterance = java.io.ByteArrayOutputStream()
        var speechActive = false
        var utteranceSamples = 0
        try {
            recorder.startRecording()
            while (running && coroutineContext.isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                val level = pcmRms(buffer, read).coerceIn(0f, 1f)
                levelsFlow.tryEmit(level)
                when (gate.onLevel(level)) {
                    SpeechGateSignal.SPEECH_STARTED -> {
                        speechActive = true
                        eventsFlow.tryEmit(VoiceTranscript.Partial(""))
                    }
                    SpeechGateSignal.SPEECH_ENDED -> {
                        if (speechActive && utterance.size() > 0) {
                            transcribe(apiKey, utterance.toByteArray())
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
                    if (utteranceSamples >= MiMoSpeechModels.ASR_SAMPLE_RATE_HZ * MAX_UTTERANCE_SECONDS) {
                        transcribe(apiKey, utterance.toByteArray())
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

    private suspend fun transcribe(apiKey: String, pcm: ByteArray) {
        if (pcm.isEmpty()) return
        val wav = pcm16ToWav(pcm, MiMoSpeechModels.ASR_SAMPLE_RATE_HZ)
        val text = transport.transcribe(apiKey, wav).trim()
        if (text.isNotBlank()) {
            eventsFlow.tryEmit(VoiceTranscript.Final(text))
        }
    }

    private fun openRecorder(minBuffer: Int): AudioRecord? {
        val recorder = runCatching {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(MiMoSpeechModels.ASR_SAMPLE_RATE_HZ)
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
        const val MAX_UTTERANCE_SECONDS = 20
    }
}
