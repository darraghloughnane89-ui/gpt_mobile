package dev.chungjungsoo.gptmobile.data.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ChatLibraryStore {
    fun observe(): Flow<ChatLibraryState>

    suspend fun current(): ChatLibraryState

    suspend fun update(transform: (ChatLibraryState) -> ChatLibraryState): ChatLibraryState
}

class MemoryChatLibraryStore(
    initial: ChatLibraryState = ChatLibraryState()
) : ChatLibraryStore {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<ChatLibraryState> = state.asStateFlow()

    override suspend fun current(): ChatLibraryState = state.value

    override suspend fun update(transform: (ChatLibraryState) -> ChatLibraryState): ChatLibraryState {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
