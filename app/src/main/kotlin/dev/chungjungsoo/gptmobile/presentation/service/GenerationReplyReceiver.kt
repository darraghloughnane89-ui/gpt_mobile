package dev.chungjungsoo.gptmobile.presentation.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.data.agent.BackgroundReplyStarter
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GenerationReplyReceiver : BroadcastReceiver() {
    @Inject
    lateinit var starter: BackgroundReplyStarter

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_REPLY_TO_CHAT) return
        val chatId = intent.getIntExtra(EXTRA_OPEN_CHAT_ID, 0)
        val text = normalizedReplyText(
            RemoteInput.getResultsFromIntent(intent)?.getCharSequence(EXTRA_REPLY_TEXT)?.toString()
        ) ?: return
        val pending = goAsync()
        val applicationContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (starter.reply(chatId, text)) {
                    applicationContext.getSystemService(NotificationManager::class.java)
                        ?.cancel(completionNotificationId(chatId))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Unable to send the notification reply.", error)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GenerationReplyReceiver"
    }
}
