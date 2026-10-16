package dev.chungjungsoo.gptmobile.presentation.phonecontrol

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.data.phonecontrol.PhoneControlEngine
import javax.inject.Inject

@AndroidEntryPoint
class RobinNotificationListenerService : NotificationListenerService() {
    @Inject
    lateinit var engine: PhoneControlEngine

    private val posted = LinkedHashMap<String, StatusBarNotification>()

    override fun onListenerConnected() {
        engine.attachNotifications(this)
    }

    override fun onListenerDisconnected() {
        engine.detachNotifications(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        posted[sbn.key] = sbn
        val title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        engine.acceptNotification(sbn.key, sbn.packageName, "$title $text".trim())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        posted.remove(sbn.key)
    }

    fun reply(text: String): Boolean {
        val notification = posted.values.lastOrNull { status ->
            status.notification.actions?.any { it.remoteInputs?.isNotEmpty() == true } == true
        } ?: return false
        val action = notification.notification.actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true } ?: return false
        val input = action.remoteInputs?.firstOrNull() ?: return false
        val fillIn = Intent()
        val results = Bundle()
        results.putCharSequence(input.resultKey, text)
        RemoteInput.addResultsToIntent(arrayOf(input), fillIn, results)
        return runCatching {
            action.actionIntent.send(this, 0, fillIn)
            true
        }.getOrDefault(false)
    }
}
