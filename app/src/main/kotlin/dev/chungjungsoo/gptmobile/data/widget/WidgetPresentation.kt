package dev.chungjungsoo.gptmobile.data.widget

sealed interface RoutineWidgetState {
    data object Locked : RoutineWidgetState
    data object Empty : RoutineWidgetState
    data class Ready(
        val name: String,
        val summary: String,
        val chat: WidgetChat
    ) : RoutineWidgetState
}

sealed interface PinnedWidgetState {
    data object Locked : PinnedWidgetState
    data object Empty : PinnedWidgetState
    data class Ready(val chats: List<WidgetChat>) : PinnedWidgetState
}

object WidgetPresentation {
    fun routine(
        deviceLocked: Boolean,
        onKeyguard: Boolean,
        allowWhenLocked: Boolean,
        result: RoutineResult?,
        liveChat: WidgetChat?,
        liveSummary: String?
    ): RoutineWidgetState {
        if (!WidgetContentPolicy.canRevealChatContent(deviceLocked, onKeyguard, allowWhenLocked)) {
            return RoutineWidgetState.Locked
        }
        if (result == null || liveChat == null) return RoutineWidgetState.Empty
        val summary = WidgetSecrets.redact(liveSummary ?: result.summary).trim()
        if (summary.isBlank()) return RoutineWidgetState.Empty
        val name = WidgetSecrets.redact(liveChat.title.ifBlank { result.name }).ifBlank { result.name }
        return RoutineWidgetState.Ready(
            name = name,
            summary = summary,
            chat = liveChat.copy(title = name)
        )
    }

    fun pinned(
        deviceLocked: Boolean,
        onKeyguard: Boolean,
        allowWhenLocked: Boolean,
        chats: List<WidgetChat>,
        pinnedIds: List<Int>,
        limit: Int
    ): PinnedWidgetState {
        if (!WidgetContentPolicy.canRevealChatContent(deviceLocked, onKeyguard, allowWhenLocked)) {
            return PinnedWidgetState.Locked
        }
        val selected = PinnedChats.select(chats, pinnedIds, limit).map { chat ->
            val safeTitle = WidgetSecrets.redact(chat.title).ifBlank { chat.title }
            chat.copy(title = safeTitle)
        }
        return if (selected.isEmpty()) PinnedWidgetState.Empty else PinnedWidgetState.Ready(selected)
    }
}
