package dev.chungjungsoo.gptmobile.presentation.voice

import android.content.Intent
import dev.chungjungsoo.gptmobile.data.voice.VoiceLaunch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object VoiceLaunchSignals {
    private val pendingState = MutableStateFlow(false)
    val pending = pendingState.asStateFlow()

    fun request() {
        pendingState.value = true
    }

    fun consume() {
        pendingState.value = false
    }
}

fun Intent.requestsVoiceChat(): Boolean = VoiceLaunch.isVoiceEntry(
    action = action,
    openVoiceExtra = getBooleanExtra(VoiceLaunch.EXTRA_OPEN_VOICE, false)
)
