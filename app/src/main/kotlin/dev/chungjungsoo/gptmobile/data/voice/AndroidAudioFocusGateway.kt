package dev.chungjungsoo.gptmobile.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidAudioFocusGateway(
    context: Context
) : AudioFocusGateway {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val focusLoss = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val focusLost = focusLoss.asSharedFlow()
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                focusLoss.tryEmit(Unit)
            }
        }
        .build()

    override fun requestPlayback(): Boolean {
        val result = audioManager.requestAudioFocus(request)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandon() {
        audioManager.abandonAudioFocusRequest(request)
    }
}
