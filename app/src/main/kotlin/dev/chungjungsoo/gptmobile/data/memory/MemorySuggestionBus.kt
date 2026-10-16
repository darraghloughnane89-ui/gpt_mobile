package dev.chungjungsoo.gptmobile.data.memory

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PendingMemorySuggestion(
    val chatId: Int,
    val text: String
)

interface MemorySuggestionSink {
    suspend fun offer(chatId: Int, text: String)

    object None : MemorySuggestionSink {
        override suspend fun offer(chatId: Int, text: String) = Unit
    }
}

@Singleton
class MemorySuggestionBus @Inject constructor() : MemorySuggestionSink {
    private val _suggestions = MutableSharedFlow<PendingMemorySuggestion>(extraBufferCapacity = 8)
    val suggestions = _suggestions.asSharedFlow()

    override suspend fun offer(chatId: Int, text: String) {
        val fact = text.trim()
        if (fact.isEmpty()) return
        _suggestions.emit(PendingMemorySuggestion(chatId, fact))
    }
}
