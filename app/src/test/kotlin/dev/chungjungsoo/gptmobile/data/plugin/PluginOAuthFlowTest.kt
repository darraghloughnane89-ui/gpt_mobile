package dev.chungjungsoo.gptmobile.data.plugin

import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthClient
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthClientTest
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.decodeURLPart
import io.ktor.serialization.kotlinx.json.json
import java.net.URI
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginOAuthFlowTest {
    @Test
    fun `begin copies discovered PKCE material into the AppAuth launch spec`() = runBlocking {
        McpOAuthClientTest.OAuthFixtureServer().use { server ->
            val httpClient = HttpClient(CIO) {
                expectSuccess = false
                install(ContentNegotiation) { json(JSON) }
            }
            val flow = PluginOAuthFlow(McpOAuthClient(httpClient))
            val redirect = pluginOAuthRedirectUri("plugin-gmail")
            val (discovery, start) = flow.begin(server.mcpUrl, allowCleartext = true, redirect, suppliedClientId = null)
            val spec = flow.launchSpec(discovery, start)
            val authorization = URI(spec.authorizationUri).rawQuery.formValues()

            assertEquals(start.pending.clientId, spec.clientId)
            assertEquals(start.pending.codeVerifier, spec.codeVerifier)
            assertEquals("S256", spec.codeChallengeMethod)
            assertEquals(discovery.resource, spec.resource)
            assertEquals(redirect, spec.redirectUri)
            assertEquals(authorization["code_challenge"], spec.codeChallenge)
            assertNotEquals(spec.codeVerifier, spec.codeChallenge)
            assertEquals(spec.state, authorization["state"])
            httpClient.close()
        }
    }

    @Test
    fun `bearer header rejects a prefix and control characters`() {
        val flow = PluginOAuthFlow(McpOAuthClient(HttpClient(CIO)))
        assertEquals("Bearer secret", flow.bearerHeader(" secret "))
        assertTrue(runCatching { flow.bearerHeader("Bearer secret") }.isFailure)
        assertTrue(runCatching { flow.bearerHeader("bad\ntoken") }.isFailure)
        assertTrue(runCatching { flow.bearerHeader(" ") }.isFailure)
    }

    @Test
    fun `plugin callback path stays distinct from tool connection callbacks`() {
        val callback = "dev.chungjungsoo.gptmobile://oauth/plugin/plugin-gmail?code=1&state=2"
        assertTrue(isPluginOAuthCallback(callback))
        assertEquals("plugin-gmail", pluginOAuthPluginUid(callback))
        assertFalse(isPluginOAuthCallback("dev.chungjungsoo.gptmobile://oauth/mcp/connection-1?code=1&state=2"))
        assertFalse(isPluginOAuthCallback(null))
        assertTrue(runCatching { pluginOAuthRedirectUri("../escape") }.isFailure)
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

private fun String.formValues(): Map<String, String> = split('&')
    .filter { it.isNotBlank() }
    .associate { item ->
        val parts = item.split('=', limit = 2)
        parts[0].decodeURLPart() to parts.getOrElse(1) { "" }.decodeURLPart()
    }
