package dev.chungjungsoo.gptmobile.data.hermes

import dev.chungjungsoo.gptmobile.data.security.SecretVault
import javax.inject.Inject
import javax.inject.Singleton

interface HermesCredentialStore {
    suspend fun read(): String?

    suspend fun write(token: String)

    suspend fun clear()
}

@Singleton
class VaultHermesCredentialStore @Inject constructor(
    private val secretVault: SecretVault
) : HermesCredentialStore {
    override suspend fun read(): String? {
        val bytes = secretVault.read(HermesDefaults.SECRET_REF) ?: return null
        return try {
            bytes.decodeToString().trim().ifBlank { null }
        } finally {
            bytes.fill(0)
        }
    }

    override suspend fun write(token: String) {
        val normalized = token.trim().removePrefix("Bearer ").trim()
        val bytes = normalized.encodeToByteArray()
        try {
            secretVault.put(HermesDefaults.SECRET_REF, bytes)
        } finally {
            bytes.fill(0)
        }
    }

    override suspend fun clear() {
        secretVault.delete(HermesDefaults.SECRET_REF)
    }
}

class MemoryHermesCredentialStore(
    initial: String? = null
) : HermesCredentialStore {
    private var token: String? = initial

    override suspend fun read(): String? = token

    override suspend fun write(token: String) {
        this.token = token.trim().removePrefix("Bearer ").trim()
    }

    override suspend fun clear() {
        token = null
    }
}
