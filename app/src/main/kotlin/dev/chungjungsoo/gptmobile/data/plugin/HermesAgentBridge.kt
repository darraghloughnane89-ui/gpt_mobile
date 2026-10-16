package dev.chungjungsoo.gptmobile.data.plugin

import dev.chungjungsoo.gptmobile.data.hermes.HermesCredentialStore
import dev.chungjungsoo.gptmobile.data.hermes.HermesDefaults
import dev.chungjungsoo.gptmobile.data.hermes.HermesProbeResult
import dev.chungjungsoo.gptmobile.data.hermes.HermesResolvedRoute
import dev.chungjungsoo.gptmobile.data.hermes.HermesRouteMode
import dev.chungjungsoo.gptmobile.data.hermes.HermesSettingsStore
import dev.chungjungsoo.gptmobile.data.hermes.HermesStreamEvent
import dev.chungjungsoo.gptmobile.data.hermes.HermesTransport
import dev.chungjungsoo.gptmobile.data.hermes.HermesUserContent
import dev.chungjungsoo.gptmobile.data.hermes.chatCompletionBody
import dev.chungjungsoo.gptmobile.data.hermes.directApiRoot
import dev.chungjungsoo.gptmobile.data.hermes.multiplexApiRoot
import dev.chungjungsoo.gptmobile.data.hermes.parseHermesEndpoint
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks Hermes through the OpenAI-compatible API server from the Hermes send
 * stream: [HermesSettingsStore], [HermesCredentialStore], and [HermesTransport].
 * There is no MCP `delegate` tool.
 */
@Singleton
class HermesAgentBridge @Inject constructor(
    private val settingsStore: HermesSettingsStore,
    private val credentials: HermesCredentialStore,
    private val transport: HermesTransport
) : HermesBridge {
    override suspend fun delegate(task: String): String {
        val settings = settingsStore.current()
        val token = credentials.read()?.trim().orEmpty()
        val endpoint = settings.endpoint.trim()
        if (endpoint.isEmpty() || token.isEmpty()) {
            return "Handoff failed: Hermes is not set up. Open Settings → Hermes and save " +
                "http://<tailscale-ip>:8642 plus the API server key. Profile ${HermesDefaults.PROFILE}."
        }
        val parsed = parseHermesEndpoint(endpoint)
            ?: return "Handoff failed: Hermes endpoint is invalid. Use http://<tailscale-ip>:8642 with no /v1 suffix."
        val profile = settings.profile.ifBlank { HermesDefaults.PROFILE }
        val route = when (val probe = transport.probe(endpoint, profile, token)) {
            is HermesProbeResult.Success -> {
                val root = if (probe.mode == HermesRouteMode.MULTIPLEX) {
                    multiplexApiRoot(parsed.base, profile)
                } else {
                    directApiRoot(parsed.base)
                }
                HermesResolvedRoute(apiRoot = root, mode = probe.mode, modelId = probe.modelId)
            }

            is HermesProbeResult.Unreachable ->
                return "Handoff failed: could not reach Hermes at $endpoint. ${probe.detail}"

            HermesProbeResult.Unauthorized ->
                return "Handoff failed: Hermes rejected the API server key."

            is HermesProbeResult.ProfileMismatch ->
                return "Handoff failed: the API server key does not belong to profile ${probe.profile}."

            HermesProbeResult.NeedsKey ->
                return "Handoff failed: Hermes needs an API server key in Settings → Hermes."

            is HermesProbeResult.Failed ->
                return "Handoff failed: Hermes connection failed. ${probe.detail}"
        }
        val sessionId = UUID.randomUUID().toString()
        val body = chatCompletionBody(
            model = route.modelId.ifBlank { profile },
            history = emptyList(),
            user = HermesUserContent(text = task)
        )
        val text = StringBuilder()
        var failure: String? = null
        try {
            transport.streamChat(
                route = route,
                token = token,
                sessionId = sessionId,
                sessionKey = "${HermesDefaults.SESSION_PREFIX}:$sessionId",
                idempotencyKey = sessionId,
                body = body,
                onAccepted = {},
                onEvent = { event ->
                    when (event) {
                        is HermesStreamEvent.Text -> text.append(event.text)
                        is HermesStreamEvent.Failed -> failure = event.message
                        is HermesStreamEvent.Tool -> Unit
                        is HermesStreamEvent.Completed -> Unit
                    }
                }
            )
        } catch (error: Exception) {
            return "Handoff failed: ${error.message ?: "Hermes request failed."}"
        }
        failure?.let { return "Handoff failed: $it" }
        return text.toString().ifBlank { "Hermes returned an empty reply." }
    }
}
