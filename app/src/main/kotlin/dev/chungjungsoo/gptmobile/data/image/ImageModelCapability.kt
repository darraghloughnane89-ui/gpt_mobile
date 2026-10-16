package dev.chungjungsoo.gptmobile.data.image

import dev.chungjungsoo.gptmobile.data.model.ClientType
import java.net.URI

enum class ImageApiKind {
    OPENAI,
    XAI,
    GEMINI,
    IMAGEN
}

data class ImageGenerationTarget(
    val kind: ImageApiKind,
    val model: String
)

object ImageModelCapability {
    fun detect(clientType: ClientType, apiUrl: String, model: String): ImageGenerationTarget? {
        val normalized = model.trim()
        if (normalized.isEmpty()) return null
        val host = hostOf(apiUrl)
        val openAi = isOpenAiImageModel(normalized)
        val xai = isXaiImageModel(normalized, host)
        val imagen = isImagenModel(normalized)
        val gemini = isGeminiImageModel(normalized)
        return when (clientType) {
            ClientType.OPENAI -> when {
                xai -> ImageGenerationTarget(ImageApiKind.XAI, normalized)
                openAi -> ImageGenerationTarget(ImageApiKind.OPENAI, normalized)
                else -> null
            }

            ClientType.GOOGLE -> when {
                imagen -> ImageGenerationTarget(ImageApiKind.IMAGEN, normalized)
                gemini -> ImageGenerationTarget(ImageApiKind.GEMINI, normalized)
                else -> null
            }

            ClientType.CUSTOM -> when {
                isGoogleHost(host) && imagen -> ImageGenerationTarget(ImageApiKind.IMAGEN, normalized)
                isGoogleHost(host) && gemini -> ImageGenerationTarget(ImageApiKind.GEMINI, normalized)
                xai -> ImageGenerationTarget(ImageApiKind.XAI, normalized)
                openAi -> ImageGenerationTarget(ImageApiKind.OPENAI, normalized)
                else -> null
            }

            else -> null
        }
    }
}

internal fun isOpenAiImageModel(model: String): Boolean {
    val value = model.lowercase().substringAfterLast('/')
    return value.startsWith("dall-e") ||
        value.startsWith("dalle") ||
        value.contains("gpt-image") ||
        value.contains("chatgpt-image")
}

internal fun isXaiImageModel(model: String, host: String): Boolean {
    val value = model.lowercase().substringAfterLast('/')
    val grokImage = value.contains("grok") && (value.contains("image") || value.contains("imagine"))
    if (grokImage) return true
    return isXaiHost(host) && (value == "aurora" || value.contains("imagine"))
}

internal fun isImagenModel(model: String): Boolean = model.lowercase().substringAfterLast('/').contains("imagen")

internal fun isGeminiImageModel(model: String): Boolean {
    val value = model.lowercase().substringAfterLast('/')
    if (value.contains("imagen")) return false
    return value.contains("gemini") && value.contains("image")
}

internal fun hostOf(apiUrl: String): String = runCatching {
    URI(apiUrl.trim()).host?.lowercase().orEmpty()
}.getOrDefault("")

internal fun isXaiHost(host: String): Boolean = host == "x.ai" || host.endsWith(".x.ai")

internal fun isGoogleHost(host: String): Boolean = host == "googleapis.com" || host.endsWith(".googleapis.com")
