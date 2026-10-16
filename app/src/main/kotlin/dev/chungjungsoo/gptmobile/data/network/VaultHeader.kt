package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import dev.chungjungsoo.gptmobile.data.security.VaultLockedException
import dev.chungjungsoo.gptmobile.data.security.VaultSession
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.util.AttributeKey

enum class ProviderAuthStyle {
    BEARER,
    X_API_KEY,
    X_GOOG_API_KEY
}

class VaultHeaderSpec(
    val secretRef: String,
    val style: ProviderAuthStyle
) {
    companion object {
        val attribute: AttributeKey<VaultHeaderSpec> = AttributeKey("VaultHeaderSpec")
    }
}

class VaultHeaderConfig {
    var vault: SecretVault? = null
    var session: VaultSession? = null
}

val VaultHeaderPlugin = createClientPlugin("VaultHeader", ::VaultHeaderConfig) {
    val vault = pluginConfig.vault
    val session = pluginConfig.session
    onRequest { request, _ ->
        val spec = request.attributes.getOrNull(VaultHeaderSpec.attribute) ?: return@onRequest
        if (session != null && !session.isUnlocked()) throw VaultLockedException()
        val secretVault = vault ?: return@onRequest
        val bytes = secretVault.read(spec.secretRef) ?: return@onRequest
        try {
            val value = bytes.decodeToString()
            when (spec.style) {
                ProviderAuthStyle.BEARER -> request.bearerAuth(value)
                ProviderAuthStyle.X_API_KEY -> request.header("x-api-key", value)
                ProviderAuthStyle.X_GOOG_API_KEY -> request.header("x-goog-api-key", value)
            }
        } finally {
            bytes.fill(0)
        }
    }
}

fun HttpRequestBuilder.authorizeProvider(config: ProviderRequestConfig, style: ProviderAuthStyle) {
    val secretRef = config.secretRef
    if (!secretRef.isNullOrBlank()) {
        attributes.put(VaultHeaderSpec.attribute, VaultHeaderSpec(secretRef, style))
        return
    }
    val token = config.token
    when (style) {
        ProviderAuthStyle.BEARER -> if (!token.isNullOrEmpty()) bearerAuth(token)
        ProviderAuthStyle.X_API_KEY -> header("x-api-key", token ?: "")
        ProviderAuthStyle.X_GOOG_API_KEY -> header("x-goog-api-key", token ?: "")
    }
}

fun PlatformV2.withInjectedSecret(
    anthropicBetaFeatures: Set<String> = emptySet(),
    resumableReplies: Boolean = false,
    onUnconfirmedRemoteCancellation: (suspend (responseId: String) -> Unit)? = null
): ProviderRequestConfig = ProviderRequestConfig(
    apiUrl = apiUrl,
    token = if (secretRef.isNullOrBlank()) token else null,
    anthropicBetaFeatures = anthropicBetaFeatures,
    resumableReplies = resumableReplies,
    onUnconfirmedRemoteCancellation = onUnconfirmedRemoteCancellation,
    secretRef = secretRef
)
