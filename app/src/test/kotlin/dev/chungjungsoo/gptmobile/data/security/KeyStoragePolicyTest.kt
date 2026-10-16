package dev.chungjungsoo.gptmobile.data.security

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyStoragePolicyTest {
    @Test
    fun vaultedPlatform_clearsTokenAndKeepsRef() {
        val platform = PlatformV2(
            name = "OpenAI",
            compatibleType = ClientType.OPENAI,
            apiUrl = "https://api.openai.com",
            token = "sk-live-secret-value",
            uid = "abc",
            model = "gpt-4o"
        )
        val ref = KeyStoragePolicy.secretRefFor(platform.uid, platform.secretRef)
        val stored = KeyStoragePolicy.vaultedPlatform(platform, ref, "OpenAI")

        assertNull(stored.token)
        assertEquals("platform_abc", stored.secretRef)
    }
}
