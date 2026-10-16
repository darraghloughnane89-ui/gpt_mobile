package dev.chungjungsoo.gptmobile.data.gguf

import kotlinx.coroutines.flow.Flow

data class GgufChatTurn(
    val role: String,
    val content: String
)

data class GgufSampling(
    val contextSize: Int,
    val temperature: Float,
    val topP: Float,
    val maxTokens: Int,
    val threads: Int
)

interface GgufRuntime {
    val isBusy: Boolean

    fun stream(
        modelPath: String,
        systemPrompt: String?,
        messages: List<GgufChatTurn>,
        sampling: GgufSampling
    ): Flow<String>
}
