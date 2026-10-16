package dev.chungjungsoo.gptmobile.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFailureClassifierTest {
    @Test
    fun classify_detectsRateLimitAuthTimeoutAndNetwork() {
        assertEquals(ProviderFailureKind.RATE_LIMIT, ProviderFailureClassifier.classify("HTTP 429: rate limit exceeded"))
        assertEquals(ProviderFailureKind.AUTH, ProviderFailureClassifier.classify("HTTP 401: Incorrect API key provided"))
        assertEquals(ProviderFailureKind.AUTH, ProviderFailureClassifier.classify("HTTP 403 forbidden"))
        assertEquals(ProviderFailureKind.TIMEOUT, ProviderFailureClassifier.classify("Request timed out."))
        assertEquals(ProviderFailureKind.TIMEOUT, ProviderFailureClassifier.classify("The response timed out. Partial output was kept."))
        assertEquals(
            ProviderFailureKind.NETWORK,
            ProviderFailureClassifier.classify("Network error: Unable to resolve host.")
        )
        assertNull(ProviderFailureClassifier.classify("Enter this model's context window before continuing."))
    }

    @Test
    fun presentChatFailure_usesFriendlyKindAndKeepsPartialAnswer() {
        val timeout = presentChatFailure("Error: Request timed out.")
        assertEquals(ProviderFailureKind.TIMEOUT, timeout?.kind)
        assertEquals("", timeout?.visibleAnswer)

        val partial = presentChatFailure("Partial answer\n\n[Response stopped: HTTP 429 too many requests]")
        assertEquals(ProviderFailureKind.RATE_LIMIT, partial?.kind)
        assertEquals("Partial answer", partial?.visibleAnswer)

        assertNull(presentChatFailure("Error: Enter this model's context window before continuing."))
        val redacted = displayChatText("Error: sk-proj-abcdefghijklmnopqrstuvwxyz")
        assertFalse(redacted.contains("sk-proj-abcdefghijklmnopqrstuvwxyz"))
        assertTrue(redacted.startsWith("Error:"))
    }
}
