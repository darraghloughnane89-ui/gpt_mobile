package dev.chungjungsoo.gptmobile.data.network

import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.security.SecretEntry
import dev.chungjungsoo.gptmobile.data.security.SecretRedactor
import dev.chungjungsoo.gptmobile.data.security.SecretType
import dev.chungjungsoo.gptmobile.data.security.SoftwareAesGcmVault
import dev.chungjungsoo.gptmobile.data.security.VaultLockedException
import dev.chungjungsoo.gptmobile.data.security.VaultSession
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.prepareGet
import java.io.File
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultHeaderPluginTest {
    @Test
    fun request_injectsVaultKeyAndLogLineDoesNotContainIt() = runBlocking {
        val secret = "sk-live-secret-value"
        val directory = File(System.getProperty("java.io.tmpdir"), "vault-${UUID.randomUUID()}")
        val vault = SoftwareAesGcmVault(directory)
        vault.store(SecretEntry("openai_key", SecretType.API_KEY, "OpenAI"), secret.encodeToByteArray())
        val seen = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/models") { exchange ->
            seen.set(exchange.requestHeaders.getFirst("Authorization"))
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            val client = NetworkClient(CIO, secretVault = vault, vaultSession = null)
            val config = ProviderRequestConfig(
                apiUrl = "http://127.0.0.1:${server.address.port}/",
                token = null,
                secretRef = "openai_key"
            )
            val status = client().prepareGet("http://127.0.0.1:${server.address.port}/models") {
                authorizeProvider(config, ProviderAuthStyle.BEARER)
            }.execute { it.status.value }

            assertEquals(200, status)
            assertEquals("Bearer $secret", seen.get())
            assertFalse(SecretRedactor.redact("Authorization: ${seen.get()}").contains(secret))
            assertNullToken(config)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun lockedVault_refusesToAttachTheKey() = runBlocking {
        val directory = File(System.getProperty("java.io.tmpdir"), "vault-${UUID.randomUUID()}")
        val vault = SoftwareAesGcmVault(directory)
        vault.put("openai_key", "sk-live-secret-value".encodeToByteArray())
        val session = VaultSession().apply { useClock { 0L } }
        val client = NetworkClient(CIO, secretVault = vault, vaultSession = session)
        val seen = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            seen.set(exchange.requestHeaders.getFirst("Authorization"))
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        try {
            val error = runCatching {
                client().prepareGet("http://127.0.0.1:${server.address.port}/") {
                    authorizeProvider(
                        ProviderRequestConfig("http://127.0.0.1/", token = null, secretRef = "openai_key"),
                        ProviderAuthStyle.BEARER
                    )
                }.execute { it.status.value }
            }.exceptionOrNull()
            assertTrue(error is VaultLockedException)
            assertEquals(null, seen.get())
        } finally {
            server.stop(0)
        }
    }

    private fun assertNullToken(config: ProviderRequestConfig) {
        assertEquals(null, config.token)
    }
}
