package dev.chungjungsoo.gptmobile.data.provider

import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.usesOpenAiCompatibleBaseUrl
import dev.chungjungsoo.gptmobile.data.network.applyCompatibleCredentials
import dev.chungjungsoo.gptmobile.data.network.googleApiRoot
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

class ModelListClient(
    private val httpClient: HttpClient
) {
    suspend fun fetch(clientType: ClientType, apiUrl: String, token: String?): List<RemoteModel> {
        val endpoint = endpoint(clientType, apiUrl)
        if (endpoint.isBlank()) return emptyList()
        val body = httpClient.prepareGet(endpoint) {
            when (clientType) {
                ClientType.GOOGLE -> header("x-goog-api-key", token.orEmpty())
                ClientType.ANTHROPIC -> {
                    header("x-api-key", token.orEmpty())
                    header("anthropic-version", "2023-06-01")
                }

                else -> applyCompatibleCredentials(endpoint, token)
            }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                error("Model list request failed (${response.status.value})")
            }
            response.bodyAsText()
        }
        return ModelListParser.parse(body)
    }

    fun endpoint(clientType: ClientType, apiUrl: String): String {
        val trimmed = apiUrl.trim()
        if (trimmed.isEmpty()) return ""
        return when (clientType) {
            ClientType.GOOGLE -> googleApiRoot(trimmed).trimEnd('/') + "/v1beta/models"
            ClientType.ANTHROPIC -> trimmed.trimEnd('/') + "/models"
            ClientType.LITERT_LM, ClientType.LLAMA_CPP -> ""
            else -> {
                val base = if (clientType.usesOpenAiCompatibleBaseUrl()) {
                    OpenAiCompatiblePresets.normalizeBaseUrl(trimmed)
                } else {
                    trimmed
                }
                base.trimEnd('/') + "/models"
            }
        }
    }
}
