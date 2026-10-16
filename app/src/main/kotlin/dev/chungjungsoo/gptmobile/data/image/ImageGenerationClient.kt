package dev.chungjungsoo.gptmobile.data.image

import dev.chungjungsoo.gptmobile.data.network.ProviderRequestConfig

fun interface ImageGenerationClient {
    suspend fun generate(
        target: ImageGenerationTarget,
        prompt: String,
        timeoutSeconds: Int,
        config: ProviderRequestConfig
    ): ImageGenerationResult
}

sealed class ImageGenerationResult {
    data class Success(
        val bytes: ByteArray,
        val mimeType: String,
        val caption: String
    ) : ImageGenerationResult()

    data class Failure(val message: String) : ImageGenerationResult()
}
