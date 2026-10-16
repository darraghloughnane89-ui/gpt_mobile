package dev.chungjungsoo.gptmobile.data.security

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2

object SecretSyncPolicy {
    const val VAULT_DIRECTORY = "secret-vault"
    const val CLEAR_PROVIDER_TOKENS_SQL = "UPDATE platform_v2 SET token = NULL"

    fun excludesPath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized.contains("/$VAULT_DIRECTORY/") ||
            normalized.endsWith("/$VAULT_DIRECTORY") ||
            normalized.endsWith(".vault") ||
            normalized.endsWith(".meta")
    }

    fun redactPlatform(platform: PlatformV2): PlatformV2 = platform.copy(token = null)
}
