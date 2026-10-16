package dev.chungjungsoo.gptmobile.data.prompt

import kotlinx.coroutines.flow.Flow

interface PromptLibraryStorage {
    suspend fun read(): String?
    suspend fun write(contents: String)
}

sealed interface PromptImportOutcome {
    data class Success(val counts: PromptImportCounts) : PromptImportOutcome
    data object Unreadable : PromptImportOutcome
}

interface PromptLibraryRepository {
    fun observe(): Flow<List<SavedPrompt>>
    suspend fun save(prompt: SavedPrompt): SavedPrompt
    suspend fun delete(id: String)
    suspend fun setFavourite(id: String, favourite: Boolean)
    suspend fun importJson(raw: String): PromptImportOutcome
    suspend fun exportJson(): String
}
