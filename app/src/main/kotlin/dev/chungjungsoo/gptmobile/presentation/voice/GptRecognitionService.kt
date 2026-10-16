package dev.chungjungsoo.gptmobile.presentation.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Recognition service required in this package so the app can be chosen as the
 * default digital assistant. Listening is forwarded to the platform recognizer.
 */
class GptRecognitionService : RecognitionService() {
    private val sessions = ConcurrentHashMap<Callback, SpeechRecognizer>()

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        val recognizer = createRecognizer()
        sessions[listener] = recognizer
        recognizer.setRecognitionListener(ForwardingListener(listener) { release(listener) })
        val intent = recognizerIntent ?: Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizer.startListening(intent)
    }

    override fun onCancel(listener: Callback?) {
        if (listener == null) return
        sessions.remove(listener)?.let { recognizer ->
            recognizer.cancel()
            recognizer.destroy()
        }
    }

    override fun onStopListening(listener: Callback?) {
        if (listener == null) return
        sessions[listener]?.stopListening()
    }

    private fun createRecognizer(): SpeechRecognizer {
        val onDevice = runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(this) }.getOrDefault(false)
        if (!onDevice) return SpeechRecognizer.createSpeechRecognizer(this)
        return runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(this) }
            .getOrElse { SpeechRecognizer.createSpeechRecognizer(this) }
    }

    private fun release(listener: Callback) {
        sessions.remove(listener)?.destroy()
    }

    private class ForwardingListener(
        private val callback: Callback,
        private val onFinished: () -> Unit
    ) : android.speech.RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            callback.readyForSpeech(params ?: Bundle())
        }

        override fun onBeginningOfSpeech() {
            callback.beginningOfSpeech()
        }

        override fun onRmsChanged(rmsdB: Float) {
            callback.rmsChanged(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            if (buffer != null) callback.bufferReceived(buffer)
        }

        override fun onEndOfSpeech() {
            callback.endOfSpeech()
        }

        override fun onError(error: Int) {
            callback.error(error)
            onFinished()
        }

        override fun onResults(results: Bundle?) {
            callback.results(results ?: Bundle())
            onFinished()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            callback.partialResults(partialResults ?: Bundle())
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
