package dev.chungjungsoo.gptmobile.data.security

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretSyncPolicyTest {
    @Test
    fun syncPayloads_dropTokensAndVaultFiles() {
        val platform = PlatformV2(
            name = "OpenAI",
            compatibleType = ClientType.OPENAI,
            enabled = true,
            apiUrl = "https://api.openai.com/v1/",
            token = "sk-live-secret-value",
            model = "gpt-5",
            secretRef = "profile_1"
        )
        val redacted = SecretSyncPolicy.redactPlatform(platform)

        assertNull(redacted.token)
        assertEquals("profile_1", redacted.secretRef)
        assertTrue(SecretSyncPolicy.excludesPath("/data/user/0/app/no_backup/secret-vault/profile_1.vault"))
        assertTrue(SecretSyncPolicy.excludesPath("/data/user/0/app/no_backup/secret-vault/profile_1.meta"))
        assertFalse(SecretSyncPolicy.excludesPath("/data/user/0/app/databases/chat_v2"))
        assertTrue(SecretSyncPolicy.CLEAR_PROVIDER_TOKENS_SQL.contains("token = NULL"))
    }
}
