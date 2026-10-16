package dev.chungjungsoo.gptmobile.data.security

import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.network.ProviderAuthStyle
import dev.chungjungsoo.gptmobile.data.network.ProviderRequestConfig
import dev.chungjungsoo.gptmobile.data.network.authorizeProvider
import io.ktor.client.request.prepareGet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderKeyProbe @Inject constructor(
    private val networkClient: NetworkClient
) {
    suspend fun test(apiUrl: String, secretRef: String, style: ProviderAuthStyle): KeyProbeResult {
        if (!networkClient.sessionUnlocked()) return KeyProbeResult.Locked
        val endpoint = apiUrl.trimEnd('/') + "/models"
        return try {
            val status = networkClient().prepareGet(endpoint) {
                authorizeProvider(
                    ProviderRequestConfig(apiUrl, token = null, secretRef = secretRef),
                    style
                )
            }.execute { response -> response.status.value }
            if (status == 401 || status == 403) KeyProbeResult.Rejected else KeyProbeResult.Accepted
        } catch (_: VaultLockedException) {
            KeyProbeResult.Locked
        } catch (_: Exception) {
            KeyProbeResult.Unreachable
        }
    }
}

enum class KeyProbeResult {
    Accepted,
    Rejected,
    Locked,
    Unreachable
}
