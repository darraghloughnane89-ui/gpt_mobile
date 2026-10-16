package dev.chungjungsoo.gptmobile.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidSpeechRecognizerEngine(
    private val context: Context
) : VoiceRecognizer {
    private val eventsFlow = MutableSharedFlow<VoiceTranscript>(extraBufferCapacity = 32)
    private val levelsFlow = MutableSharedFlow<Float>(extraBufferCapacity = 8)
    override val events = eventsFlow.asSharedFlow()
    override val levels = levelsFlow.asSharedFlow()
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var session = 0
    private var consecutiveFailures = 0

    override suspend fun start() {
        handler.post {
            listening = true
            session += 1
            consecutiveFailures = 0
            listen(session)
        }
    }

    override suspend fun stop() {
        handler.post {
            listening = false
            session += 1
            recognizer?.cancel()
        }
    }

    private fun listen(activeSession: Int) {
        if (!listening || activeSession != session) return
        val speechRecognizer = ensureRecognizer() ?: return
        val preferOnDevice = runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOnDevice)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { speechRecognizer.startListening(intent) }
            .onFailure {
                eventsFlow.tryEmit(
                    VoiceTranscript.Failure(
                        message = "Speech recognition is unavailable on this phone.",
                        recoverable = false
                    )
                )
            }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        val created = runCatching {
            if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }.getOrElse {
            runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        }
        if (created == null) {
            eventsFlow.tryEmit(
                VoiceTranscript.Failure(
                    message = "Speech recognition is unavailable on this phone.",
                    recoverable = false
                )
            )
            return null
        }
        created.setRecognitionListener(listener)
        recognizer = created
        return created
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            val clamped = rmsdB.coerceIn(-2f, 10f)
            levelsFlow.tryEmit((clamped + 2f) / 12f)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (!listening) return
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                consecutiveFailures = 0
                scheduleRestart()
                return
            }
            consecutiveFailures += 1
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || consecutiveFailures >= 4) {
                listening = false
                eventsFlow.tryEmit(
                    VoiceTranscript.Failure(
                        message = recognitionErrorMessage(error),
                        recoverable = error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                    )
                )
                return
            }
            scheduleRestart()
        }

        override fun onResults(results: Bundle?) {
            consecutiveFailures = 0
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
                .orEmpty()
            if (text.isNotBlank()) {
                eventsFlow.tryEmit(VoiceTranscript.Final(text))
            }
            scheduleRestart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull { it.isNotBlank() }
                .orEmpty()
            if (text.isNotBlank()) {
                eventsFlow.tryEmit(VoiceTranscript.Partial(text))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun scheduleRestart() {
        val activeSession = session
        handler.postDelayed({ listen(activeSession) }, RESTART_DELAY_MS)
    }

    private fun recognitionErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition lost its network connection."
        SpeechRecognizer.ERROR_AUDIO -> "The microphone could not be opened."
        else -> "Speech recognition stopped."
    }

    private companion object {
        const val RESTART_DELAY_MS = 250L
    }
}
