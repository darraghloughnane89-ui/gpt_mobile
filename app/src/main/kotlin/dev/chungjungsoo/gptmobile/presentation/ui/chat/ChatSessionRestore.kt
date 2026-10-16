package dev.chungjungsoo.gptmobile.presentation.ui.chat

internal const val COMPOSER_DRAFT_KEY = "chat_composer_draft"

internal fun restoreComposerDraft(saved: String?): String? = saved?.takeIf { it.isNotEmpty() }

/**
 * Tracks a send so stop can abandon it even when the chat row does not exist yet.
 * A later send is not treated as cancelled.
 */
internal class StreamCancelGate {
    private var generation = 0
    private var cancelledThrough = -1

    fun beginSend(): Int {
        generation += 1
        return generation
    }

    fun cancel() {
        cancelledThrough = generation
        generation += 1
    }

    fun wasExplicitlyCancelled(token: Int): Boolean = token <= cancelledThrough
}
