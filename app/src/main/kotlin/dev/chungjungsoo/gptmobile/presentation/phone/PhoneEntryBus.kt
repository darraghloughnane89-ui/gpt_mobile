package dev.chungjungsoo.gptmobile.presentation.phone

import android.content.Intent
import dev.chungjungsoo.gptmobile.data.phone.PhoneLinks
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Buffers phone deep links until navigation is ready. A cold start publishes
 * from [android.app.Activity.onCreate] before the Compose graph is collecting.
 */
object PhoneEntryBus {
    private val channel = Channel<Intent>(capacity = Channel.BUFFERED)
    val events = channel.receiveAsFlow()

    fun publish(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        if (!PhoneLinks.isPhoneDeepLink(data.scheme, data.host)) return
        channel.trySend(intent)
    }
}
