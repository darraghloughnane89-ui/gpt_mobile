package dev.chungjungsoo.gptmobile.presentation.ui.portable

import android.content.Intent
import dev.chungjungsoo.gptmobile.data.portable.PortableImportInbox
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

object PortableIntents {
    const val EXTRA_PORTABLE_IMPORT = "dev.chungjungsoo.gptmobile.extra.PORTABLE_IMPORT"

    fun openPreview(context: android.content.Context): Intent =
        Intent().setClassName(context, "dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity").apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_PORTABLE_IMPORT, true)
        }
}

/**
 * Tells the nav host that [PortableImportInbox] has a preview waiting.
 * Buffered so a cold start can publish before Compose collects.
 */
object PortableImportBus {
    private val channel = Channel<Unit>(capacity = Channel.BUFFERED)
    val events = channel.receiveAsFlow()

    fun publish() {
        channel.trySend(Unit)
    }
}
