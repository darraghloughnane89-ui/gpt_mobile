package dev.chungjungsoo.gptmobile.data.widget

interface WidgetBoardStore {
    suspend fun read(): WidgetBoard
    suspend fun writePinned(ids: List<Int>)
    suspend fun writeShowContentWhenLocked(enabled: Boolean)
    suspend fun writeLatestRoutine(result: RoutineResult)
}
