package dev.chungjungsoo.gptmobile.data.oauth

import dev.chungjungsoo.gptmobile.data.security.SecretVault
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ProviderOAuthSession(
    val kind: String,
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtEpochMs: Long? = null,
    val tokenEndpoint: String? = null
)

@Singleton
class ProviderOAuthSessionStore @Inject constructor(
    private val vault: SecretVault
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun reference(platformUid: String): String = "oauth_$platformUid"

    suspend fun read(platformUid: String): ProviderOAuthSession? {
        val bytes = vault.read(reference(platformUid)) ?: return null
        return try {
            runCatching { json.decodeFromString<ProviderOAuthSession>(bytes.decodeToString()) }.getOrNull()
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun save(platformUid: String, session: ProviderOAuthSession) {
        val encoded = json.encodeToString(session).encodeToByteArray()
        try {
            vault.put(reference(platformUid), encoded)
        } finally {
            encoded.fill(0)
        }
    }

    suspend fun clear(platformUid: String) {
        vault.delete(reference(platformUid))
    }
}
