package dev.chungjungsoo.gptmobile.presentation.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import dev.chungjungsoo.gptmobile.data.voice.VoiceLaunch
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity

class GptVoiceInteractionSession(
    context: Context
) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val launch = Intent(context, MainActivity::class.java).apply {
            action = VoiceLaunch.ACTION_ASSIST
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(VoiceLaunch.EXTRA_OPEN_VOICE, true)
        }
        runCatching { startAssistantActivity(launch) }
            .onFailure { error ->
                Log.w(TAG, "Assistant activity launch failed", error)
                runCatching { context.startActivity(launch) }
            }
        hide()
    }

    private companion object {
        const val TAG = "VoiceAssistant"
    }
}
