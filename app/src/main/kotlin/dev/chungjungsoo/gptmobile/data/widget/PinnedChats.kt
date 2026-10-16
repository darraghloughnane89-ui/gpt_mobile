package dev.chungjungsoo.gptmobile.data.widget

object PinnedChats {
    fun toggle(current: List<Int>, chatIds: List<Int>, pin: Boolean): List<Int> {
        val selected = chatIds.filter { it > 0 }.distinct()
        if (selected.isEmpty()) return current
        return if (pin) {
            (selected.asReversed() + current).distinct()
        } else {
            val remove = selected.toSet()
            current.filter { it !in remove }
        }
    }

    fun select(chats: List<WidgetChat>, pinnedIds: List<Int>, limit: Int): List<WidgetChat> {
        if (limit <= 0 || pinnedIds.isEmpty()) return emptyList()
        val byId = chats.associateBy { it.id }
        return pinnedIds.asSequence()
            .mapNotNull { byId[it] }
            .distinctBy { it.id }
            .take(limit)
            .toList()
    }
}
