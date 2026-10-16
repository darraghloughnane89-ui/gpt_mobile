package dev.chungjungsoo.gptmobile.data.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class MiMoSpeechSpeaker(
    private val transport: MiMoSpeechTransport,
    private val keys: VoiceSpeechKeyStore,
    private val preferences: VoiceSpeechPreferences,
    private val sink: PcmSink,
    private val bargeIn: BargeInEvents
) : VoiceSpeaker {
    private var playback: Job? = null
    private var activeUtterance: String? = null

    override suspend fun speak(utteranceId: String, text: String) {
        if (text.isBlank()) return
        stop()
        val apiKey = keys.readKey() ?: throw MiMoSpeechException("Add a MiMo key to speak with MiMo.")
        val voice = MiMoSpeechModels.knownVoice(preferences.settings.first().voiceId)
        val job = Job(currentCoroutineContext()[Job])
        playback = job
        activeUtterance = utteranceId
        try {
            val result = withContext(job + Dispatchers.IO) {
                StreamingPlayback.play(
                    chunks = transport.synthesize(apiKey, text, voice),
                    sink = sink,
                    sampleRateHz = MiMoSpeechModels.TTS_SAMPLE_RATE_HZ,
                    interrupt = bargeIn.speech()
                )
            }
            if (result == PlaybackResult.EMPTY) {
                throw MiMoSpeechException("MiMo returned no audio.")
            }
        } finally {
            if (playback === job) playback = null
            if (activeUtterance == utteranceId) {
                sink.stop()
                activeUtterance = null
            }
        }
    }

    override suspend fun stop() {
        playback?.cancel()
        playback = null
        sink.stop()
    }
}

fun interface BargeInEvents {
    fun speech(): kotlinx.coroutines.flow.Flow<Unit>
}
