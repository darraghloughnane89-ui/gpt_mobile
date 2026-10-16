package dev.chungjungsoo.gptmobile.data.plugin

import dev.chungjungsoo.gptmobile.data.hermes.HermesDefaults
import dev.chungjungsoo.gptmobile.data.hermes.HermesProbeResult
import dev.chungjungsoo.gptmobile.data.hermes.HermesResolvedRoute
import dev.chungjungsoo.gptmobile.data.hermes.HermesRouteMode
import dev.chungjungsoo.gptmobile.data.hermes.HermesSettings
import dev.chungjungsoo.gptmobile.data.hermes.HermesStreamEvent
import dev.chungjungsoo.gptmobile.data.hermes.HermesTransport
import dev.chungjungsoo.gptmobile.data.hermes.MemoryHermesCredentialStore
import dev.chungjungsoo.gptmobile.data.hermes.MemoryHermesSettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesAgentBridgeTest {
    @Test
    fun delegateWithoutSettingsDoesNotCallTheApi() = runBlocking {
        val transport = RecordingTransport()
        val bridge = HermesAgentBridge(
            MemoryHermesSettingsStore(),
            MemoryHermesCredentialStore(),
            transport
        )

        val reply = bridge.delegate("ping")

        assertTrue(reply.startsWith("Handoff failed"))
        assertEquals(0, transport.probes)
        assertEquals(0, transport.chats)
    }

    @Test
    fun delegateUsesTheSavedApiServerAndReturnsTheStreamedReply() = runBlocking {
        val transport = RecordingTransport()
        val bridge = HermesAgentBridge(
            MemoryHermesSettingsStore(
                HermesSettings(endpoint = "http://100.64.1.8:8642", profile = HermesDefaults.PROFILE)
            ),
            MemoryHermesCredentialStore("api-server-key-value"),
            transport
        )

        val reply = bridge.delegate("Reply with pong")

        assertEquals("pong", reply)
        assertEquals(1, transport.probes)
        assertEquals("http://100.64.1.8:8642/v1", transport.lastRoute?.apiRoot)
        assertEquals("cbre-admin", transport.lastRoute?.modelId)
        assertTrue(transport.lastBody.orEmpty().contains("Reply with pong"))
        assertTrue(transport.lastSessionKey.orEmpty().startsWith("${HermesDefaults.SESSION_PREFIX}:"))
    }

    @Test
    fun personalPresetsHaveNoLocalhostDefault() {
        PluginCatalog.personalPresets.forEach { preset ->
            assertEquals("", preset.endpointUrl)
            assertTrue(preset.needsServer)
            assertTrue(preset.urlPlaceholder.isNotEmpty())
        }
        assertTrue(PluginCatalog.personalPresets.none { it.id == "hermes" })
        assertTrue(PluginCatalog.slack.verified.not())
        assertTrue(PluginCatalog.box.verified.not())
        assertEquals("https://gmailmcp.googleapis.com/mcp/v1", PluginCatalog.gmail.endpointUrl)
    }

    private class RecordingTransport : HermesTransport {
        var probes = 0
        var chats = 0
        var lastRoute: HermesResolvedRoute? = null
        var lastBody: String? = null
        var lastSessionKey: String? = null

        override suspend fun probe(endpoint: String, profile: String, token: String): HermesProbeResult {
            probes += 1
            return HermesProbeResult.Success(HermesRouteMode.DIRECT, profile, streaming = true)
        }

        override suspend fun streamChat(
            route: HermesResolvedRoute,
            token: String,
            sessionId: String,
            sessionKey: String,
            idempotencyKey: String,
            body: String,
            onAccepted: suspend () -> Unit,
            onEvent: suspend (HermesStreamEvent) -> Unit
        ) {
            chats += 1
            lastRoute = route
            lastBody = body
            lastSessionKey = sessionKey
            onAccepted()
            onEvent(HermesStreamEvent.Text("pong"))
            onEvent(HermesStreamEvent.Completed(null))
        }
    }
}
