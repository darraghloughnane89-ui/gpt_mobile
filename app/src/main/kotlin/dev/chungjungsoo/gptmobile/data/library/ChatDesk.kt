package dev.chungjungsoo.gptmobile.data.library

import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import kotlinx.coroutines.flow.Flow

interface ChatDesk {
    val library: Flow<ChatLibraryState>

    suspend fun updateLibrary(transform: (ChatLibraryState) -> ChatLibraryState): ChatLibraryState

    suspend fun currentLibrary(): ChatLibraryState

    suspend fun searchExcerpts(query: String): Map<Int, String>
}

class DefaultChatDesk(
    private val store: ChatLibraryStore,
    private val messageV2Dao: MessageV2Dao
) : ChatDesk {
    override val library: Flow<ChatLibraryState> = store.observe()

    override suspend fun updateLibrary(transform: (ChatLibraryState) -> ChatLibraryState): ChatLibraryState = store.update(transform)

    override suspend fun currentLibrary(): ChatLibraryState = store.current()

    override suspend fun searchExcerpts(query: String): Map<Int, String> {
        if (query.isBlank()) return emptyMap()
        val hits = messageV2Dao.searchMessageContents(escapeLikeQuery(query)).map { hit ->
            hit.chatId to hit.content
        }
        return buildSearchExcerpts(hits, query)
    }
}
