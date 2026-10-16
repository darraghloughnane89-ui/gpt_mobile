package dev.chungjungsoo.gptmobile.data.security

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2

object KeyStoragePolicy {
    fun secretRefFor(uid: String, existing: String?): String =
        existing?.takeIf { it.isNotBlank() } ?: "platform_$uid"

    fun vaultedPlatform(platform: PlatformV2, secretRef: String, label: String): PlatformV2 =
        platform.copy(token = null, secretRef = secretRef, name = label)
}
