package dev.chungjungsoo.gptmobile.data.fetch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LinkChip(
    val url: String,
    val title: String,
    val kind: LinkKind,
    val text: String,
    val status: Status
) {
    enum class Status {
        READING,
        READY,
        FAILED
    }

    fun toReadLink(): ReadLink? = if (status == Status.READY && text.isNotBlank()) {
        ReadLink(url = url, finalUrl = url, title = title, text = text, kind = kind)
    } else {
        null
    }
}

/**
 * Composer chips for pages the user asked to read. Nothing here is persisted on its own.
 * A sent turn stores the fenced text in the existing message content column.
 */
class LinkContextController(
    private val maxLinks: Int = MAX_LINKS
) {
    private val _chips = MutableStateFlow<List<LinkChip>>(emptyList())
    val chips: StateFlow<List<LinkChip>> = _chips.asStateFlow()

    fun snapshot(): List<LinkChip> = _chips.value

    fun isReading(): Boolean = _chips.value.any { it.status == LinkChip.Status.READING }

    fun hasReady(): Boolean = _chips.value.any { it.status == LinkChip.Status.READY }

    fun begin(url: String): Boolean {
        val normalized = LinkUrls.firstHttpUrl(url) ?: return false
        synchronized(this) {
            val current = _chips.value
            val existing = current.firstOrNull { it.url == normalized }
            if (existing?.status == LinkChip.Status.READING || existing?.status == LinkChip.Status.READY) {
                return false
            }
            val withoutFailed = current.filterNot { it.url == normalized }
            val occupied = withoutFailed.count { it.status != LinkChip.Status.FAILED }
            if (occupied >= maxLinks && existing == null) return false
            _chips.value = withoutFailed + LinkChip(
                url = normalized,
                title = normalized,
                kind = LinkKind.PAGE,
                text = "",
                status = LinkChip.Status.READING
            )
            return true
        }
    }

    fun succeed(url: String, document: ReadLink) {
        _chips.update { current ->
            current.map { chip ->
                if (chip.url != url || chip.status != LinkChip.Status.READING) {
                    chip
                } else {
                    chip.copy(
                        title = document.title.ifBlank { url },
                        kind = document.kind,
                        text = document.text,
                        status = LinkChip.Status.READY
                    )
                }
            }
        }
    }

    fun fail(url: String) {
        _chips.update { current ->
            current.map { chip ->
                if (chip.url == url && chip.status == LinkChip.Status.READING) {
                    chip.copy(status = LinkChip.Status.FAILED, text = "")
                } else {
                    chip
                }
            }
        }
    }

    fun remove(url: String) {
        _chips.update { current -> current.filterNot { it.url == url } }
    }

    fun clear() {
        _chips.value = emptyList()
    }

    /**
     * Returns the user message to store, or null when there is nothing the user asked to send.
     * A fetched page never sends on its own.
     */
    fun messageContent(question: String, sendingAttachments: Boolean): String? {
        val ready = _chips.value.mapNotNull { it.toReadLink() }
        val beside = visibleQuestion(question, ready.map { it.url })
        if (ready.isEmpty()) {
            if (beside.isBlank() && !sendingAttachments) return null
            return beside
        }
        if (beside.isBlank()) return null
        return LinkMessage.compose(beside, ready)
    }

    private fun visibleQuestion(question: String, urls: List<String>): String {
        var remaining = question.trim()
        urls.forEach { url ->
            remaining = remaining.replace(url, " ")
        }
        return remaining.replace(Regex("\\s+"), " ").trim()
    }

    private companion object {
        const val MAX_LINKS = 3
    }
}
