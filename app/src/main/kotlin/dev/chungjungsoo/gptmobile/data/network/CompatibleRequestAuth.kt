package dev.chungjungsoo.gptmobile.data.network

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header

internal fun HttpRequestBuilder.applyCompatibleCredentials(apiUrl: String, token: String?) {
    val value = token?.takeIf { it.isNotBlank() } ?: return
    bearerAuth(value)
    // Xiaomi's curl examples authenticate with api-key. Bearer is also sent because the OpenAI SDK path uses it.
    if (apiUrl.contains("xiaomimimo.com", ignoreCase = true)) {
        header("api-key", value)
    }
}
