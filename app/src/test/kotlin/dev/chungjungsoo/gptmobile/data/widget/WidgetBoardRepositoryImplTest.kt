package dev.chungjungsoo.gptmobile.data.widget

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetBoardRepositoryImplTest {
    @Test
    fun setPinned_persistsAndRefreshes() = runBlocking {
        val store = MemoryWidgetBoardStore()
        val refresher = CountingRefresher()
        val repository = WidgetBoardRepositoryImpl(store, refresher)

        repository.setPinned(listOf(5, 6), pinned = true)
        repository.setPinned(listOf(5), pinned = false)

        assertEquals(listOf(6), repository.pinnedChatIds())
        assertEquals(2, refresher.count)
    }

    @Test
    fun saveLatestRoutine_redactsBeforePersistence() = runBlocking {
        val store = MemoryWidgetBoardStore()
        val repository = WidgetBoardRepositoryImpl(store, CountingRefresher())

        repository.saveLatestRoutine(
            RoutineResult(
                routineId = "morning-briefing",
                name = "Morning briefing",
                summary = "Leave at 7. sk-proj-donotstorethiskey",
                chatId = 2,
                enabledPlatforms = listOf("platform"),
                completedAtEpochSeconds = 1
            )
        )

        val saved = repository.board().latestRoutine!!
        assertFalse(saved.summary.contains("sk-proj-"))
        assertTrue(saved.summary.contains("Leave at 7."))
    }

    @Test
    fun lockScreenOptIn_defaultsOff() = runBlocking {
        val repository = WidgetBoardRepositoryImpl(MemoryWidgetBoardStore(), CountingRefresher())

        assertFalse(repository.showContentWhenLocked())
        repository.setShowContentWhenLocked(true)
        assertTrue(repository.showContentWhenLocked())
    }

    private class MemoryWidgetBoardStore(
        private var current: WidgetBoard = WidgetBoard()
    ) : WidgetBoardStore {
        override suspend fun read(): WidgetBoard = current
        override suspend fun writePinned(ids: List<Int>) {
            current = current.copy(pinnedChatIds = ids)
        }
        override suspend fun writeShowContentWhenLocked(enabled: Boolean) {
            current = current.copy(showContentWhenLocked = enabled)
        }
        override suspend fun writeLatestRoutine(result: RoutineResult) {
            current = current.copy(latestRoutine = result)
        }
    }

    private class CountingRefresher : WidgetRefresher {
        var count = 0
        override suspend fun refresh() {
            count += 1
        }
    }
}
