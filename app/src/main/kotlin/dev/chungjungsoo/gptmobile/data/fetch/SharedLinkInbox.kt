package dev.chungjungsoo.gptmobile.data.fetch

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** URL handed in from the share sheet. The chat that is on screen consumes it. */
@Singleton
class SharedLinkInbox @Inject constructor() {
    private val pendingUrl = AtomicReference<String?>(null)
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(raw: String?) {
        val url = LinkUrls.firstHttpUrl(raw.orEmpty()) ?: return
        pendingUrl.set(url)
        _pending.value = url
    }

    fun consume(): String? {
        val taken = pendingUrl.getAndSet(null)
        _pending.value = pendingUrl.get()
        return taken
    }
}
