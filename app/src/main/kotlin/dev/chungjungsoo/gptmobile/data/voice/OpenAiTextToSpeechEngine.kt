package dev.chungjungsoo.gptmobile.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenAiTextToSpeechEngine(
    private val context: Context,
    private val credentials: VoiceCredentialLookup,
    private val client: OpenAiAudioClient
) : TextToSpeechEngine {
    private val lock = Any()
    private var player: MediaPlayer? = null
    private var completion: CompletableDeferred<Unit>? = null

    override suspend fun speak(utteranceId: String, text: String) {
        if (text.isBlank()) return
        val credential = credentials.openAiCredential() ?: error("OpenAI speech is unavailable")
        val audio = client.synthesize(credential, text)
        val file = File(context.cacheDir, "voice-${UUID.randomUUID()}.mp3")
        withContext(Dispatchers.IO) { file.writeBytes(audio) }
        val deferred = CompletableDeferred<Unit>()
        val mediaPlayer = MediaPlayer()
        synchronized(lock) {
            player = mediaPlayer
            completion = deferred
        }
        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.setOnCompletionListener { finish(deferred) }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                finish(deferred)
                true
            }
            mediaPlayer.prepare()
            mediaPlayer.start()
            deferred.await()
        } finally {
            synchronized(lock) {
                if (player === mediaPlayer) player = null
                if (completion === deferred) completion = null
            }
            runCatching { mediaPlayer.release() }
            file.delete()
        }
    }

    override suspend fun stop() {
        val current = synchronized(lock) { player }
        val pending = synchronized(lock) { completion }
        runCatching { current?.stop() }
        finish(pending)
    }

    private fun finish(deferred: CompletableDeferred<Unit>?) {
        if (deferred != null && !deferred.isCompleted) deferred.complete(Unit)
    }
}
