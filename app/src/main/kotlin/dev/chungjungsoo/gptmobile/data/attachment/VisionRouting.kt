package dev.chungjungsoo.gptmobile.data.attachment

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.model.ClientType

enum class ProviderTransport {
    OPENAI_RESPONSES,
    OPENAI_CHAT,
    OPENAI_IMAGES,
    ANTHROPIC,
    GOOGLE,
    LITERT
}

object VisionHosts {
    fun isMiMo(apiUrl: String): Boolean = apiUrl.lowercase().contains("xiaomimimo.com")

    fun prefersChatCompletions(apiUrl: String): Boolean = isMiMo(apiUrl)

    fun miMoApiUrl(): String = ModelConstants.MIMO_API_URL
}

object ImageGeneration {
    fun usesOpenAiImagesEndpoint(compatibleType: ClientType, model: String, apiUrl: String): Boolean {
        if (!isOpenAiImageModel(model)) return false
        if (VisionHosts.isMiMo(apiUrl)) return false
        return when (compatibleType) {
            ClientType.OPENAI, ClientType.CUSTOM, ClientType.OPENROUTER -> true
            else -> false
        }
    }

    fun isOpenAiImageModel(model: String): Boolean {
        val normalized = model.lowercase()
        return normalized.startsWith("dall-e") || normalized.startsWith("gpt-image")
    }

    fun isGeminiImageModel(model: String): Boolean {
        val normalized = model.lowercase()
        return normalized.contains("imagen") || normalized.contains("-image")
    }
}

fun providerTransport(compatibleType: ClientType, apiUrl: String, model: String): ProviderTransport {
    if (ImageGeneration.usesOpenAiImagesEndpoint(compatibleType, model, apiUrl)) {
        return ProviderTransport.OPENAI_IMAGES
    }
    return when (compatibleType) {
        ClientType.OPENAI -> if (VisionHosts.prefersChatCompletions(apiUrl)) {
            ProviderTransport.OPENAI_CHAT
        } else {
            ProviderTransport.OPENAI_RESPONSES
        }

        ClientType.GROQ, ClientType.OLLAMA, ClientType.OPENROUTER, ClientType.CUSTOM -> ProviderTransport.OPENAI_CHAT

        ClientType.ANTHROPIC -> ProviderTransport.ANTHROPIC

        ClientType.GOOGLE -> ProviderTransport.GOOGLE

        ClientType.LITERT_LM -> ProviderTransport.LITERT
    }
}

fun openAiImageDataUrl(mimeType: String, base64Data: String): String = "data:$mimeType;base64,$base64Data"

data class GeneratedImagePayload(
    val mimeType: String,
    val base64Data: String
)

fun decodeGeneratedImage(value: String?, fallbackMimeType: String = "image/png"): GeneratedImagePayload? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    if (raw.startsWith("data:image/", ignoreCase = true)) {
        val headerEnd = raw.indexOf(',')
        if (headerEnd <= 0) return null
        val header = raw.substring(5, headerEnd)
        val mimeType = header.substringBefore(';').ifBlank { fallbackMimeType }
        val payload = raw.substring(headerEnd + 1).replace("\n", "").replace("\r", "")
        if (!header.contains("base64", ignoreCase = true) || payload.isBlank()) return null
        return GeneratedImagePayload(mimeType, payload)
    }
    val compact = raw.replace("\n", "").replace("\r", "")
    if (compact.length < 32 || compact.any { it !in BASE64_CHARS }) return null
    return GeneratedImagePayload(fallbackMimeType, compact)
}

private val BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=".toSet()
