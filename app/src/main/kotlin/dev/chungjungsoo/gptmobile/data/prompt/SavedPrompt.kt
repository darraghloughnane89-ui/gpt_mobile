package dev.chungjungsoo.gptmobile.data.prompt

import kotlinx.serialization.Serializable

@Serializable
data class SavedPrompt(
    val id: String,
    val title: String,
    val body: String,
    val favourite: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class PromptLibraryDocument(
    val version: Int = CURRENT_VERSION,
    val prompts: List<SavedPrompt> = emptyList()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
