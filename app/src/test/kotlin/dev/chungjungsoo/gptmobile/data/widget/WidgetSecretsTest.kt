package dev.chungjungsoo.gptmobile.data.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetSecretsTest {
    @Test
    fun redact_stripsProviderKeysAndLeavesTheSentence() {
        val raw = "Morning plan. Key sk-proj-abcDEF123456 is in the note. Token: super-secret-value"

        val redacted = WidgetSecrets.redact(raw)

        assertFalse(redacted.contains("sk-proj-"))
        assertFalse(redacted.contains("super-secret-value"))
        assertEquals("Morning plan. Key ${WidgetSecrets.REDACTED} is in the note. ${WidgetSecrets.REDACTED}", redacted)
    }

    @Test
    fun redact_stripsBearerAndPrivateKeyBlocks() {
        val raw = "Bearer abc.def-ghi and -----BEGIN PRIVATE KEY-----\nsecret\n-----END PRIVATE KEY----- done"

        val redacted = WidgetSecrets.redact(raw)

        assertFalse(redacted.contains("abc.def"))
        assertFalse(redacted.contains("BEGIN PRIVATE KEY"))
        assertTrueStartsWith(redacted, WidgetSecrets.REDACTED)
    }

    private fun assertTrueStartsWith(text: String, prefix: String) {
        org.junit.Assert.assertTrue(text.startsWith(prefix))
    }
}
