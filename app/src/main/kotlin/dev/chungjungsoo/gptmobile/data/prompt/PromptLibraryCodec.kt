package dev.chungjungsoo.gptmobile.data.prompt

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PromptLibraryCodec {
    const val MAX_PROMPTS = 500
    const val MAX_TITLE_LENGTH = 80
    const val MAX_BODY_LENGTH = 20_000

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        isLenient = true
    }

    fun encode(prompts: List<SavedPrompt>): String = json.encodeToString(
        PromptLibraryDocument(
            version = PromptLibraryDocument.CURRENT_VERSION,
            prompts = prompts
        )
    )

    fun decode(raw: String): PromptLibraryDocument? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return PromptLibraryDocument()
        return try {
            when {
                trimmed.startsWith("[") -> PromptLibraryDocument(
                    prompts = json.decodeFromString<List<SavedPrompt>>(trimmed)
                )

                else -> json.decodeFromString<PromptLibraryDocument>(trimmed)
            }
        } catch (error: SerializationException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        }
    }
}

data class PromptImportCounts(
    val added: Int,
    val updated: Int,
    val skipped: Int
)

fun libraryOrder(prompts: List<SavedPrompt>): List<SavedPrompt> = prompts.sortedWith(
    compareByDescending<SavedPrompt> { it.favourite }
        .thenBy { it.title.lowercase() }
        .thenBy { it.id }
)

fun sanitizeImportedPrompt(prompt: SavedPrompt): SavedPrompt? {
    val id = prompt.id.trim()
    val title = prompt.title.trim().take(PromptLibraryCodec.MAX_TITLE_LENGTH)
    val body = prompt.body.trim().take(PromptLibraryCodec.MAX_BODY_LENGTH)
    if (id.isEmpty() || id.length > 80 || title.isEmpty() || body.isEmpty()) return null
    if (!id.all { it.isLetterOrDigit() || it == '-' || it == '_' }) return null
    return prompt.copy(
        id = id,
        title = title,
        body = body,
        favourite = prompt.favourite
    )
}
