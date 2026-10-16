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

    @Test
    fun redact_removesGitHubGoogleSlackAndAwsKeys() {
        val github = "ghp_" + "a".repeat(20)
        val google = "AIza" + "b".repeat(30)
        val slack = "xoxb-1234567890-abcdef"
        val aws = "AKIAIOSFODNN7EXAMPLE"
        val message = "gh $github google $google slack $slack aws $aws"
        val redacted = SecretRedactor.redact(message)

        assertFalse(redacted.contains(github))
        assertFalse(redacted.contains(google))
        assertFalse(redacted.contains(slack))
        assertFalse(redacted.contains(aws))
    }
}
