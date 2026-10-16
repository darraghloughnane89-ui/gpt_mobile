package dev.chungjungsoo.gptmobile.data.image

import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val IMAGE_ERROR_LIMIT = 180

internal fun redactSecrets(text: String, secret: String?): String {
    if (secret.isNullOrBlank()) return text
    return text.replace(secret, "[redacted]")
}

internal fun imageHttpError(status: Int, body: String, secret: String?): String {
    val compact = redactSecrets(body, secret).replace(Regex("\\s+"), " ").trim().take(IMAGE_ERROR_LIMIT)
    if (compact.isBlank() || looksLikeImagePayload(compact)) {
        return "Image generation failed (HTTP $status)."
    }
    return "Image generation failed (HTTP $status): $compact"
}

internal fun decodeImageBase64(value: String): ByteArray? = runCatching {
    Base64.getDecoder().decode(value.trim())
}.getOrNull()?.takeIf { it.isNotEmpty() }

internal fun sniffImageMime(bytes: ByteArray): String? {
    if (bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte()
    ) {
        return "image/png"
    }
    if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
        return "image/jpeg"
    }
    if (bytes.size >= 12 &&
        bytes.decodeRange(0, 4) == "RIFF" &&
        bytes.decodeRange(8, 12) == "WEBP"
    ) {
        return "image/webp"
    }
    if (bytes.size >= 6 && bytes.decodeRange(0, 6).startsWith("GIF")) return "image/gif"
    return null
}

internal fun extensionForMime(mimeType: String): String = when (mimeType.substringBefore(';').trim().lowercase()) {
    "image/jpeg", "image/jpg" -> "jpg"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    else -> "png"
}

internal fun galleryDisplayName(fileName: String, mimeType: String): String {
    val cleaned = fileName
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('_', '.')
        .take(80)
        .ifBlank { "generated-image" }
    val extension = extensionForMime(mimeType)
    val current = cleaned.substringAfterLast('.', "")
    return if (current.equals(extension, ignoreCase = true)) cleaned else "$cleaned.$extension"
}

internal fun inSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= maxDimension && height / (sample * 2) >= maxDimension) {
        sample *= 2
    }
    return sample
}

internal fun isAllowedImageDownloadUrl(url: String): Boolean = runCatching {
    val uri = java.net.URI(url.trim())
    val host = uri.host?.lowercase().orEmpty()
    when (uri.scheme?.lowercase()) {
        "https" -> host.isNotEmpty()
        "http" -> host == "127.0.0.1" || host == "localhost"
        else -> false
    }
}.getOrDefault(false)

private fun looksLikeImagePayload(value: String): Boolean {
    if (value.length < 80) return false
    return listOf("b64_json", "inlineData", "bytesBase64Encoded", "iVBORw0KGgo").any(value::contains)
}

private fun ByteArray.decodeRange(start: Int, end: Int): String = String(this, start, end - start, Charsets.US_ASCII)

@Serializable
internal data class OpenAiImageRequest(
    val model: String,
    val prompt: String,
    val n: Int? = null,
    val size: String? = null,
    @SerialName("response_format")
    val responseFormat: String? = null
)

@Serializable
internal data class OpenAiImageResponse(
    val data: List<OpenAiImageDatum> = emptyList(),
    val error: OpenAiImageError? = null
)

@Serializable
internal data class OpenAiImageDatum(
    @SerialName("b64_json")
    val b64Json: String? = null,
    val url: String? = null,
    @SerialName("revised_prompt")
    val revisedPrompt: String? = null
)

@Serializable
internal data class OpenAiImageError(
    val message: String? = null
)

@Serializable
internal data class GeminiImageRequest(
    val contents: List<GeminiImageContent>,
    val generationConfig: GeminiImageConfig
)

@Serializable
internal data class GeminiImageContent(
    val role: String,
    val parts: List<GeminiImagePart>
)

@Serializable
internal data class GeminiImagePart(
    val text: String
)

@Serializable
internal data class GeminiImageConfig(
    val responseModalities: List<String>
)

@Serializable
internal data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val error: GeminiErrorBody? = null
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiCandidateContent? = null
)

@Serializable
internal data class GeminiCandidateContent(
    val parts: List<GeminiResponsePart> = emptyList()
)

@Serializable
internal data class GeminiResponsePart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@Serializable
internal data class GeminiInlineData(
    val mimeType: String? = null,
    val data: String? = null
)

@Serializable
internal data class GeminiErrorBody(
    val message: String? = null
)

@Serializable
internal data class ImagenPredictRequest(
    val instances: List<ImagenInstance>,
    val parameters: ImagenParameters
)

@Serializable
internal data class ImagenInstance(
    val prompt: String
)

@Serializable
internal data class ImagenParameters(
    val sampleCount: Int
)

@Serializable
internal data class ImagenPredictResponse(
    val predictions: List<ImagenPrediction> = emptyList(),
    val error: GeminiErrorBody? = null
)

@Serializable
internal data class ImagenPrediction(
    val bytesBase64Encoded: String? = null,
    val mimeType: String? = null
)

internal data class DecodedProviderImage(
    val bytes: ByteArray,
    val mimeType: String,
    val caption: String
)
