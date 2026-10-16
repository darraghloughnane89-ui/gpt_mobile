package dev.chungjungsoo.gptmobile.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactorTest {
    private val openAiKey = "sk-proj-abcdefghijklmnopqrstuvwxyz"
    private val anthropicKey = "sk-ant-api03-abcdefghijklmnopqrstuvwxyz"
    private val googleKey = "AIzaSyA1234567890abcdefghijklmnopqrst"
    private val groqKey = "gsk_abcdefghijklmnopqrstuvwxyz123456"
    private val huggingFaceKey = "hf_abcdefghijklmnopqrstuvwxyz"
    private val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"

    @Test
    fun redact_stripsApiKeysTokensAndPasswords() {
        val raw = """
            Authorization: Bearer $openAiKey
            api_key=$anthropicKey
            token: $groqKey
            password=hunter2hunter2
            "password": "s3cret-value"
            https://user:s3cret@api.example.com/v1?api_key=$googleKey
            hf $huggingFaceKey
            jwt $jwt
        """.trimIndent()

        val redacted = DiagnosticRedactor.redact(raw)

        assertFalse(redacted.contains(openAiKey))
        assertFalse(redacted.contains(anthropicKey))
        assertFalse(redacted.contains(googleKey))
        assertFalse(redacted.contains(groqKey))
        assertFalse(redacted.contains(huggingFaceKey))
        assertFalse(redacted.contains(jwt))
        assertFalse(redacted.contains("hunter2hunter2"))
        assertFalse(redacted.contains("s3cret-value"))
        assertFalse(redacted.contains("s3cret@"))
        assertTrue(redacted.contains(DiagnosticRedactor.REDACTED))
        assertTrue(redacted.contains("password="))
    }

    @Test
    fun redact_stripsChatContentByDefault() {
        val raw = """
            User: please remember my diary entry about the river
            Assistant: I saved the river story
            {"role":"user","content":"hello from the transcript"}
            prompt: "write a poem about seals"
        """.trimIndent()

        val redacted = DiagnosticRedactor.redact(raw)

        assertFalse(redacted.contains("diary entry"))
        assertFalse(redacted.contains("river story"))
        assertFalse(redacted.contains("hello from the transcript"))
        assertFalse(redacted.contains("poem about seals"))
        assertTrue(redacted.contains(DiagnosticRedactor.REDACTED_CHAT))
        assertTrue(redacted.contains("User:"))
    }

    @Test
    fun redact_keepsChatWhenExplicitlyRequestedAndStillStripsSecrets() {
        val raw = "User: my key is $openAiKey and the note stays"

        val redacted = DiagnosticRedactor.redact(raw, includeChatContent = true)

        assertTrue(redacted.contains("User: my key is"))
        assertTrue(redacted.contains("note stays"))
        assertFalse(redacted.contains(openAiKey))
        assertFalse(redacted.contains(DiagnosticRedactor.REDACTED_CHAT))
    }

    @Test
    fun redact_keepsStackFramesAndExceptionTypes() {
        val raw = """
            exception: java.lang.IllegalStateException
            message: Request timed out.
            at dev.chungjungsoo.gptmobile.data.network.OpenAIAPIImpl.stream(OpenAIAPIImpl.kt:120)
        """.trimIndent()

        val redacted = DiagnosticRedactor.redact(raw)

        assertEquals(raw, redacted)
    }
}
