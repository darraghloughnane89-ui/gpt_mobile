package dev.chungjungsoo.gptmobile.qa

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Scripted speech recognizer used by the debug APK.
 * Voice smoke tests select it with `settings put secure voice_recognition_service`.
 */
class FakeRecognitionService : RecognitionService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        main.post {
            val spoken = Bundle().apply {
                putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(TRANSCRIPT))
                putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(1f))
            }
            runCatching {
                listener.readyForSpeech(Bundle())
                listener.beginningOfSpeech()
                listener.partialResults(spoken)
                listener.results(spoken)
            }
        }
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback) = Unit

    companion object {
        const val TRANSCRIPT = "hello from the harness"
    }
}
