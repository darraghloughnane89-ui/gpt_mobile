package dev.chungjungsoo.gptmobile.data.security

import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretVaultTest {
    @Test
    fun softwareKeystore_roundTripsTypedSecretsAndKeepsThemOutOfMetadata() = runBlocking {
        val vault = vault()
        val apiKey = "sk-live-secret-value".encodeToByteArray()
        val password = "correct-horse-battery".encodeToByteArray()
        try {
            val apiEntry = vault.store(SecretEntry("openai_key", SecretType.API_KEY, "OpenAI", endpoint = "https://api.openai.com/v1"), apiKey)
            val passwordEntry = vault.store(SecretEntry("site_login", SecretType.PASSWORD, "Example"), password)

            assertEquals(apiKey.toList(), vault.read("openai_key")?.toList())
            assertEquals(password.toList(), vault.read("site_login")?.toList())
            assertEquals(SecretType.API_KEY, apiEntry.type)
            assertEquals(SecretType.PASSWORD, passwordEntry.type)
            assertFalse(apiEntry.masked.contains("sk-live-secret-value"))
            assertFalse(passwordEntry.masked.contains("correct-horse"))
            val metadata = File(directory, "openai_key.meta").readText()
            assertFalse(metadata.contains("sk-live-secret-value"))
            assertEquals(setOf(SecretType.API_KEY, SecretType.PASSWORD), vault.entries().map { it.type }.toSet())

            vault.delete("site_login")
            assertNull(vault.read("site_login"))
        } finally {
            apiKey.fill(0)
            password.fill(0)
        }
    }

    @Test
    fun softwareKeystore_rejectsRecordBoundToAnotherReference() = runBlocking {
        val vault = vault()
        vault.put("first_key", "one-secret".encodeToByteArray())
        val moved = File(directory, "first_key.vault").readBytes()
        File(directory, "second_key.vault").writeBytes(moved)

        val error = runCatching { vault.read("second_key") }.exceptionOrNull()
        assertTrue(error is SecretVaultException)
    }

    @Test
    fun vaultSession_expiresAfterTimeout() {
        val session = VaultSession()
        var now = 1_000L
        session.useClock { now }
        session.unlock()
        assertTrue(session.isUnlocked())
        now += VaultSession.TIMEOUT_MILLIS
        assertFalse(session.isUnlocked())
        session.lock()
        assertFalse(session.isUnlocked())
    }

    @Test
    fun keyAttempts_fallBackFromStrongBoxAndUserAuth() {
        assertEquals(
            listOf(true to true, false to true, false to false),
            VaultKeyPolicy.attempts(strongBox = true, userAuth = true)
        )
        assertEquals(listOf(false to false), VaultKeyPolicy.attempts(strongBox = false, userAuth = false))
    }

    private val directory = File(System.getProperty("java.io.tmpdir"), "vault-${UUID.randomUUID()}")
    private fun vault() = SoftwareAesGcmVault(directory)
}
