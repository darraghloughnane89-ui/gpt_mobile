package dev.chungjungsoo.gptmobile.data.obsidian.github

import dev.chungjungsoo.gptmobile.data.security.SecretVault
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObsidianGitHubTokenStore @Inject constructor(
    private val secretVault: SecretVault
) {
    suspend fun save(token: String) {
        secretVault.put(SECRET_REF, token.encodeToByteArray())
    }

    suspend fun read(): String? {
        val bytes = secretVault.read(SECRET_REF) ?: return null
        return try {
            bytes.decodeToString().trim().takeIf { it.isNotEmpty() }
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun hasToken(): Boolean = !read().isNullOrBlank()

    companion object {
        const val SECRET_REF = "obsidian_github_token"
    }
}
