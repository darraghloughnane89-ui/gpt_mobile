package dev.chungjungsoo.gptmobile.data.phone

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One in-memory handoff from the share sheet, camera entry, or a deep link
 * into the chat composer. Nothing is written to Room until the user sends.
 */
@Singleton
class PhoneHandoffStore @Inject constructor() {
    data class Handoff(
        val id: Long = System.nanoTime(),
        val targetChatId: Int,
        val text: String = "",
        val filePaths: List<String> = emptyList(),
        val launchCamera: Boolean = false
    )

    private val slot = AtomicReference<Handoff?>(null)
    private val _pending = MutableStateFlow<Handoff?>(null)
    val pending: StateFlow<Handoff?> = _pending.asStateFlow()

    fun offer(handoff: Handoff) {
        slot.set(handoff)
        _pending.value = handoff
    }

    fun consume(id: Long): Handoff? {
        val current = slot.get() ?: return null
        if (current.id != id) return null
        if (!slot.compareAndSet(current, null)) return null
        _pending.value = null
        return current
    }

    fun peek(): Handoff? = slot.get()
}
