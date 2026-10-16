package dev.chungjungsoo.gptmobile.data.bot

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object StarterPrompts {
    const val MAX_COUNT = 8
    const val MAX_LENGTH = 200

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(raw).sanitize()
        } catch (error: SerializationException) {
            emptyList()
        }
    }

    fun encode(prompts: List<String>): String = json.encodeToString(prompts.sanitize())

    private fun List<String>.sanitize(): List<String> = map { prompt -> prompt.trim().take(MAX_LENGTH) }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_COUNT)
}
