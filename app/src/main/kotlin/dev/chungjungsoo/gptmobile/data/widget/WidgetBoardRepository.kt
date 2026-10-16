package dev.chungjungsoo.gptmobile.data.widget

interface WidgetRefresher {
    suspend fun refresh()
}

interface WidgetBoardRepository {
    suspend fun board(): WidgetBoard
    suspend fun showContentWhenLocked(): Boolean
    suspend fun setShowContentWhenLocked(enabled: Boolean)
    suspend fun pinnedChatIds(): List<Int>
    suspend fun setPinned(chatIds: List<Int>, pinned: Boolean)
    suspend fun saveLatestRoutine(result: RoutineResult)
    suspend fun refreshWidgets()
}

class WidgetBoardRepositoryImpl(
    private val store: WidgetBoardStore,
    private val refresher: WidgetRefresher
) : WidgetBoardRepository {
    override suspend fun board(): WidgetBoard = store.read()

    override suspend fun showContentWhenLocked(): Boolean = store.read().showContentWhenLocked

    override suspend fun setShowContentWhenLocked(enabled: Boolean) {
        store.writeShowContentWhenLocked(enabled)
        refresher.refresh()
    }

    override suspend fun pinnedChatIds(): List<Int> = store.read().pinnedChatIds

    override suspend fun setPinned(chatIds: List<Int>, pinned: Boolean) {
        val next = PinnedChats.toggle(store.read().pinnedChatIds, chatIds, pinned)
        store.writePinned(next)
        refresher.refresh()
    }

    override suspend fun saveLatestRoutine(result: RoutineResult) {
        store.writeLatestRoutine(
            result.copy(summary = WidgetSecrets.redact(result.summary))
        )
        refresher.refresh()
    }

    override suspend fun refreshWidgets() {
        refresher.refresh()
    }
}
