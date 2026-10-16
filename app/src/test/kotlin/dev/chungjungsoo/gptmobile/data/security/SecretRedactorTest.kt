package dev.chungjungsoo.gptmobile.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SecretRedactorTest {
    @Test
    fun redact_removesBearerTokensProviderKeysAndApiKeyHeaders() {
        val secret = "sk-live-secret-value"
        val message = "Authorization: Bearer $secret\nX-Api-Key: $secret\nx-goog-api-key=$secret"
        val redacted = SecretRedactor.redact(message)

        assertFalse(redacted.contains(secret))
        assertEquals(
            "Authorization: Bearer [redacted]\nX-Api-Key: [redacted]\nx-goog-api-key=[redacted]",
            redacted
        )
    }
}
