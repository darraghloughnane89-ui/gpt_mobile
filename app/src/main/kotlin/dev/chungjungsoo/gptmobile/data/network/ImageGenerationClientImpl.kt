package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.image.DecodedProviderImage
import dev.chungjungsoo.gptmobile.data.image.GeminiGenerateResponse
import dev.chungjungsoo.gptmobile.data.image.GeminiImageConfig
import dev.chungjungsoo.gptmobile.data.image.GeminiImageContent
import dev.chungjungsoo.gptmobile.data.image.GeminiImagePart
import dev.chungjungsoo.gptmobile.data.image.GeminiImageRequest
import dev.chungjungsoo.gptmobile.data.image.ImageApiKind
import dev.chungjungsoo.gptmobile.data.image.ImageGenerationClient
import dev.chungjungsoo.gptmobile.data.image.ImageGenerationResult
import dev.chungjungsoo.gptmobile.data.image.ImageGenerationTarget
import dev.chungjungsoo.gptmobile.data.image.ImagenInstance
import dev.chungjungsoo.gptmobile.data.image.ImagenParameters
import dev.chungjungsoo.gptmobile.data.image.ImagenPredictRequest
import dev.chungjungsoo.gptmobile.data.image.ImagenPredictResponse
import dev.chungjungsoo.gptmobile.data.image.OpenAiImageRequest
import dev.chungjungsoo.gptmobile.data.image.OpenAiImageResponse
import dev.chungjungsoo.gptmobile.data.image.decodeImageBase64
import dev.chungjungsoo.gptmobile.data.image.imageHttpError
import dev.chungjungsoo.gptmobile.data.image.isAllowedImageDownloadUrl
import dev.chungjungsoo.gptmobile.data.image.redactSecrets
import dev.chungjungsoo.gptmobile.data.image.sniffImageMime
import dev.chungjungsoo.gptmobile.util.applyPlatformStreamingTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class ImageGenerationClientImpl @Inject constructor(
    private val networkClient: NetworkClient
) : ImageGenerationClient {
    override suspend fun generate(
        target: ImageGenerationTarget,
        prompt: String,
        timeoutSeconds: Int,
        config: ProviderRequestConfig
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        try {
            when (target.kind) {
                ImageApiKind.OPENAI, ImageApiKind.XAI -> postOpenAiCompatible(target, prompt, timeoutSeconds, config)
                ImageApiKind.GEMINI -> postGemini(target, prompt, timeoutSeconds, config)
                ImageApiKind.IMAGEN -> postImagen(target, prompt, timeoutSeconds, config)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            ImageGenerationResult.Failure(redactSecrets(error.message ?: "Image generation failed.", config.token))
        }
    }

    private suspend fun postOpenAiCompatible(
        target: ImageGenerationTarget,
        prompt: String,
        timeoutSeconds: Int,
        config: ProviderRequestConfig
    ): ImageGenerationResult {
        val gptImage = target.model.lowercase().let { it.contains("gpt-image") || it.contains("chatgpt-image") }
        val request = OpenAiImageRequest(
            model = target.model,
            prompt = prompt,
            n = 1,
            size = if (target.kind == ImageApiKind.OPENAI) "1024x1024" else null,
            responseFormat = if (target.kind == ImageApiKind.XAI || !gptImage) "b64_json" else null
        )
        val body = postJson(
            endpoint = openAiImagesEndpoint(config.apiUrl),
            payload = NetworkClient.json.encodeToString(request),
            timeoutSeconds = timeoutSeconds,
            kind = target.kind,
            config = config
        )
        val parsed = runCatching { NetworkClient.json.decodeFromString<OpenAiImageResponse>(body.text) }.getOrNull()
        if (!body.successful) {
            val message = parsed?.error?.message?.takeIf { it.isNotBlank() } ?: body.text
            return ImageGenerationResult.Failure(imageHttpError(body.status, message, config.token))
        }
        val datum = parsed?.data?.firstOrNull()
            ?: return ImageGenerationResult.Failure("The provider did not return an image.")
        val encoded = datum.b64Json?.let(::decodeImageBase64)
        val caption = redactSecrets(datum.revisedPrompt.orEmpty(), config.token)
        if (encoded != null) {
            return success(encoded, sniffImageMime(encoded) ?: "image/png", caption)
        }
        val url = datum.url?.takeIf { it.isNotBlank() }
            ?: return ImageGenerationResult.Failure("The provider did not return an image.")
        return downloadImage(url, caption, config.token)
    }

    private suspend fun postGemini(
        target: ImageGenerationTarget,
        prompt: String,
        timeoutSeconds: Int,
        config: ProviderRequestConfig
    ): ImageGenerationResult {
        val request = GeminiImageRequest(
            contents = listOf(
                GeminiImageContent(
                    role = "user",
                    parts = listOf(GeminiImagePart(text = prompt))
                )
            ),
            generationConfig = GeminiImageConfig(responseModalities = listOf("TEXT", "IMAGE"))
        )
        val body = postJson(
            endpoint = geminiGenerateEndpoint(config.apiUrl, target.model),
            payload = NetworkClient.json.encodeToString(request),
            timeoutSeconds = timeoutSeconds,
            kind = target.kind,
            config = config
        )
        val parsed = runCatching { NetworkClient.json.decodeFromString<GeminiGenerateResponse>(body.text) }.getOrNull()
        if (!body.successful) {
            val message = parsed?.error?.message?.takeIf { it.isNotBlank() } ?: body.text
            return ImageGenerationResult.Failure(imageHttpError(body.status, message, config.token))
        }
        val parts = parsed?.candidates?.firstOrNull()?.content?.parts.orEmpty()
        val inline = parts.firstNotNullOfOrNull { part ->
            val inlineData = part.inlineData ?: return@firstNotNullOfOrNull null
            val bytes = inlineData.data?.let(::decodeImageBase64) ?: return@firstNotNullOfOrNull null
            bytes to inlineData.mimeType
        }
        val caption = redactSecrets(parts.mapNotNull { it.text?.takeIf(String::isNotBlank) }.joinToString("\n"), config.token)
        if (inline == null) return ImageGenerationResult.Failure("The provider did not return an image.")
        val (bytes, declared) = inline
        return success(bytes, sniffImageMime(bytes) ?: declared ?: "image/png", caption)
    }

    private suspend fun postImagen(
        target: ImageGenerationTarget,
        prompt: String,
        timeoutSeconds: Int,
        config: ProviderRequestConfig
    ): ImageGenerationResult {
        val request = ImagenPredictRequest(
            instances = listOf(ImagenInstance(prompt = prompt)),
            parameters = ImagenParameters(sampleCount = 1)
        )
        val body = postJson(
            endpoint = imagenPredictEndpoint(config.apiUrl, target.model),
            payload = NetworkClient.json.encodeToString(request),
            timeoutSeconds = timeoutSeconds,
            kind = target.kind,
            config = config
        )
        val parsed = runCatching { NetworkClient.json.decodeFromString<ImagenPredictResponse>(body.text) }.getOrNull()
        if (!body.successful) {
            val message = parsed?.error?.message?.takeIf { it.isNotBlank() } ?: body.text
            return ImageGenerationResult.Failure(imageHttpError(body.status, message, config.token))
        }
        val prediction = parsed?.predictions?.firstOrNull()
        val bytes = prediction?.bytesBase64Encoded?.let(::decodeImageBase64)
            ?: return ImageGenerationResult.Failure("The provider did not return an image.")
        return success(bytes, sniffImageMime(bytes) ?: prediction.mimeType ?: "image/png", "")
    }

    private suspend fun downloadImage(url: String, caption: String, secret: String?): ImageGenerationResult {
        if (!isAllowedImageDownloadUrl(url)) {
            return ImageGenerationResult.Failure("The provider returned an image link that cannot be saved.")
        }
        return networkClient().prepareGet(url) {
            applyPlatformStreamingTimeout(60)
        }.execute { response ->
            if (!response.status.isSuccess()) {
                return@execute ImageGenerationResult.Failure("Image generation failed (HTTP ${response.status.value}).")
            }
            val bytes = response.bodyAsBytes()
            val declared = response.contentType()?.withoutParameters()?.toString()
            success(bytes, sniffImageMime(bytes) ?: declared ?: "image/png", caption)
        }
    }

    private suspend fun postJson(
        endpoint: String,
        payload: String,
        timeoutSeconds: Int,
        kind: ImageApiKind,
        config: ProviderRequestConfig
    ): RawImageResponse = networkClient().preparePost(endpoint) {
        applyPlatformStreamingTimeout(timeoutSeconds)
        contentType(ContentType.Application.Json)
        injectImageCredential(kind, config.token)
        setBody(payload)
    }.execute { response ->
        RawImageResponse(
            status = response.status.value,
            successful = response.status.isSuccess(),
            text = response.bodyAsText()
        )
    }

    private fun success(bytes: ByteArray, mimeType: String, caption: String): ImageGenerationResult {
        if (bytes.isEmpty()) return ImageGenerationResult.Failure("The provider did not return an image.")
        val image = DecodedProviderImage(bytes, mimeType, caption)
        return ImageGenerationResult.Success(image.bytes, image.mimeType, image.caption)
    }

    private data class RawImageResponse(
        val status: Int,
        val successful: Boolean,
        val text: String
    )
}

internal fun HttpRequestBuilder.injectImageCredential(kind: ImageApiKind, token: String?) {
    when (kind) {
        ImageApiKind.GEMINI, ImageApiKind.IMAGEN -> header(GOOGLE_IMAGE_API_KEY_HEADER, token ?: "")
        ImageApiKind.OPENAI, ImageApiKind.XAI -> token?.takeIf { it.isNotBlank() }?.let { bearerAuth(it) }
    }
}

internal const val GOOGLE_IMAGE_API_KEY_HEADER = "x-goog-api-key"

internal fun openAiImagesEndpoint(apiUrl: String): String = "${apiUrl.trim().trimEnd('/')}/images/generations"

internal fun geminiGenerateEndpoint(apiUrl: String, model: String): String =
    "${googleApiRoot(apiUrl)}/v1beta/models/${model.trim().encodeURLPathPart()}:generateContent"

internal fun imagenPredictEndpoint(apiUrl: String, model: String): String =
    "${googleApiRoot(apiUrl)}/v1beta/models/${model.trim().encodeURLPathPart()}:predict"
