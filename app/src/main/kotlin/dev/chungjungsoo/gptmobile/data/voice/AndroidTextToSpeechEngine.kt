package dev.chungjungsoo.gptmobile.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

class AndroidTextToSpeechEngine(
    context: Context
) : TextToSpeechEngine {
    private val ready = CompletableDeferred<Boolean>()
    private val completions = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ttsLanguage()
            ttsAudioAttributes()
            ready.complete(true)
        } else {
            ready.complete(false)
        }
    }

    override suspend fun speak(utteranceId: String, text: String) {
        if (text.isBlank()) return
        if (!ready.await()) error("Text to speech is unavailable")
        val completion = CompletableDeferred<Unit>()
        completions[utteranceId] = completion
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId)
        if (result == TextToSpeech.ERROR) {
            completions.remove(utteranceId)
            error("Text to speech failed")
        }
        try {
            completion.await()
        } finally {
            completions.remove(utteranceId)
        }
    }

    override suspend fun stop() {
        tts.stop()
        completions.values.forEach { completion ->
            if (!completion.isCompleted) completion.complete(Unit)
        }
    }

    fun shutdown() {
        tts.shutdown()
    }

    private fun ttsLanguage() {
        val language = tts.setLanguage(Locale.getDefault())
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.language = Locale.US
        }
    }

    private fun ttsAudioAttributes() {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                finish(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                finish(utteranceId)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                finish(utteranceId)
            }
        })
    }

    private fun finish(utteranceId: String?) {
        if (utteranceId == null) return
        val completion = completions[utteranceId] ?: return
        if (!completion.isCompleted) completion.complete(Unit)
    }
}
