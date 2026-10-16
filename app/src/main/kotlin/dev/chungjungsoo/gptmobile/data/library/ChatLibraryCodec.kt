package dev.chungjungsoo.gptmobile.data.library

import kotlinx.serialization.json.Json

object ChatLibraryCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(state: ChatLibraryState): String = json.encodeToString(state)

    fun decode(raw: String?): ChatLibraryState {
        if (raw.isNullOrBlank()) return ChatLibraryState()
        return try {
            json.decodeFromString<ChatLibraryState>(raw)
        } catch (_: IllegalArgumentException) {
            ChatLibraryState()
        }
    }
}
