package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.hermes.HermesDefaults
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.util.AttributeKey

class VaultHeaderSpec(
    val secretRef: String
) {
    companion object {
        val attribute: AttributeKey<VaultHeaderSpec> = AttributeKey("VaultHeaderSpec")
    }
}

class VaultHeaderConfig {
    var vault: SecretVault? = null
}

val VaultHeaderPlugin = createClientPlugin("VaultHeader", ::VaultHeaderConfig) {
    val vault = pluginConfig.vault
    onRequest { request, _ ->
        val spec = request.attributes.getOrNull(VaultHeaderSpec.attribute) ?: return@onRequest
        val secretVault = vault ?: return@onRequest
        val bytes = secretVault.read(spec.secretRef) ?: return@onRequest
        try {
            request.bearerAuth(bytes.decodeToString())
        } finally {
            bytes.fill(0)
        }
    }
}

fun HttpRequestBuilder.authorizeProvider(config: ProviderRequestConfig) {
    val secretRef = config.secretRef
    if (!secretRef.isNullOrBlank()) {
        attributes.put(VaultHeaderSpec.attribute, VaultHeaderSpec(secretRef))
        return
    }
    val token = config.token
    if (!token.isNullOrEmpty()) bearerAuth(token)
}

fun PlatformV2.hermesChatRequestConfig(): ProviderRequestConfig {
    if (uid != HermesDefaults.PLATFORM_UID && secretRef != HermesDefaults.SECRET_REF) {
        return ProviderRequestConfig(apiUrl, token)
    }
    return ProviderRequestConfig(
        apiUrl = apiUrl,
        token = null,
        secretRef = HermesDefaults.SECRET_REF
    )
}
