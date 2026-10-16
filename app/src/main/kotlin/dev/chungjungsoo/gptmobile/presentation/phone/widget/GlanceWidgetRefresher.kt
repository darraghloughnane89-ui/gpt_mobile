package dev.chungjungsoo.gptmobile.presentation.phone.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import dev.chungjungsoo.gptmobile.data.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GlanceWidgetRefresher(
    private val context: Context
) : WidgetRefresher {
    override suspend fun refresh() {
        refreshWidget(GptMobileWidget())
        refreshWidget(RoutineResultWidget())
        refreshWidget(PinnedChatsWidget())
    }

    private suspend inline fun <reified T : GlanceAppWidget> refreshWidget(widget: T) {
        runCatching {
            GlanceAppWidgetManager(context).getGlanceIds(T::class.java).forEach { glanceId ->
                widget.update(context, glanceId)
            }
        }
    }
}

internal object WidgetLockEvents {
    @Volatile
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        registered = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            context.applicationContext,
            WidgetLockReceiver(),
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
}

private class WidgetLockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                GlanceWidgetRefresher(context.applicationContext).refresh()
            } finally {
                pending.finish()
            }
        }
    }
}
