package dev.chungjungsoo.gptmobile.data.attachment

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ImageGenerationRequest(
    @SerialName("model")
    val model: String,

    @SerialName("prompt")
    val prompt: String,

    @SerialName("n")
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val count: Int = 1,

    @SerialName("size")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val size: String? = null,

    @SerialName("response_format")
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val responseFormat: String? = null
)

@Serializable
data class ImageGenerationResponse(
    @SerialName("data")
    val data: List<ImageGenerationDatum> = emptyList()
)

@Serializable
data class ImageGenerationDatum(
    @SerialName("b64_json")
    val b64Json: String? = null,

    @SerialName("url")
    val url: String? = null
)

fun imageGenerationRequest(model: String, prompt: String): ImageGenerationRequest {
    val gptImage = model.lowercase().startsWith("gpt-image")
    return ImageGenerationRequest(
        model = model,
        prompt = prompt,
        size = "1024x1024",
        responseFormat = if (gptImage) null else "b64_json"
    )
}
