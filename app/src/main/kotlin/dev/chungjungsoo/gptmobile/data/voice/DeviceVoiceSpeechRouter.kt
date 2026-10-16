package dev.chungjungsoo.gptmobile.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class AndroidSpeechRecognizerEngine(
    private val context: Context
) : VoiceRecognizer {
    private val eventsFlow = MutableSharedFlow<VoiceTranscript>(extraBufferCapacity = 16)
    private val levelsFlow = MutableSharedFlow<Float>(extraBufferCapacity = 8)
    override val events = eventsFlow
    override val levels = levelsFlow
    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun start() {
        withContext(Dispatchers.Main) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                eventsFlow.emit(
                    VoiceTranscript.Failure(
                        message = "Speech recognition isn’t available on this phone.",
                        recoverable = false
                    )
                )
                return@withContext
            }
            val active = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { created ->
                created.setRecognitionListener(listener)
                recognizer = created
            }
            active.startListening(listenIntent())
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.Main) {
            recognizer?.cancel()
        }
    }

    fun release() {
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
        }
    }

    private fun listenIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            val level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            levelsFlow.tryEmit(level)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
            eventsFlow.tryEmit(
                VoiceTranscript.Failure(
                    message = if (recoverable) "Didn’t catch that." else "Speech recognition stopped.",
                    recoverable = recoverable
                )
            )
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
                .orEmpty()
            eventsFlow.tryEmit(VoiceTranscript.Final(text))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
                .orEmpty()
            if (text.isNotEmpty()) eventsFlow.tryEmit(VoiceTranscript.Partial(text))
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}

class AndroidTextToSpeechEngine(
    context: Context
) : VoiceSpeaker {
    private val engine = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }
    private var ready = false

    override suspend fun speak(utteranceId: String, text: String) {
        if (text.isBlank()) return
        withContext(Dispatchers.Main) {
            if (!ready) return@withContext
            suspendCancellableCoroutine { continuation ->
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(doneId: String?) {
                        if (doneId == utteranceId && continuation.isActive) continuation.resume(Unit)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(errorId: String?) {
                        if (errorId == utteranceId && continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onStop(stoppedId: String?, interrupted: Boolean) {
                        if (stoppedId == utteranceId && continuation.isActive) continuation.resume(Unit)
                    }
                })
                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
                if (result == TextToSpeech.ERROR && continuation.isActive) continuation.resume(Unit)
                continuation.invokeOnCancellation { engine.stop() }
            }
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.Main) {
            engine.stop()
        }
    }

    fun shutdown() {
        engine.shutdown()
    }
}

class DeviceVoiceSpeechRouter @Inject constructor(
    @param:ApplicationContext context: Context,
    gate: NotificationCaptureGate
) : VoiceSpeechRouterHost {
    private val deviceRecognizer = AndroidSpeechRecognizerEngine(context)
    private val deviceSpeaker = AndroidTextToSpeechEngine(context)
    override val recognizer: VoiceRecognizer = GatedVoiceRecognizer(gate, deviceRecognizer)
    override val speaker: VoiceSpeaker = StoppingSpeaker(recognizer, deviceSpeaker)

    override suspend fun refresh() = Unit

    private class StoppingSpeaker(
        private val recognizer: VoiceRecognizer,
        private val speaker: VoiceSpeaker
    ) : VoiceSpeaker {
        override suspend fun speak(utteranceId: String, text: String) {
            recognizer.stop()
            speaker.speak(utteranceId, text)
        }

        override suspend fun stop() {
            speaker.stop()
        }
    }
}
