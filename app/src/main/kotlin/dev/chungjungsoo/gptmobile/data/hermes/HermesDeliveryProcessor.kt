package dev.chungjungsoo.gptmobile.data.hermes

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HermesDeliveryProcessor @Inject constructor(
    private val threads: HermesThreadStore,
    private val settingsStore: HermesSettingsStore,
    private val credentials: HermesCredentialStore,
    private val transport: HermesTransport,
    private val attachmentRoots: HermesAttachmentRoots = HermesAttachmentRoots { emptyList() }
) {
    suspend fun drain(attempt: Int): Boolean {
        threads.update { HermesThreadReducer.recover(it) }
        val settings = settingsStore.current()
        val token = credentials.read()?.trim().orEmpty()
        if (settings.endpoint.isBlank() || token.isBlank()) return false
        val route = resolveRoute(settings, token)
        if (route == null) {
            if (attempt >= HermesDefaults.MAX_AUTO_ATTEMPTS - 1) {
                failQueued("Could not reach Hermes.")
                return false
            }
            return true
        }
        while (true) {
            val pending = HermesThreadReducer.nextPending(threads.snapshot.value) ?: return false
            threads.update { HermesThreadReducer.markSending(it, pending.assistant.id) }
            val retry = deliver(pending, route, token, attempt)
            if (retry) return true
        }
    }

    private suspend fun deliver(
        pending: HermesPending,
        route: HermesResolvedRoute,
        token: String,
        attempt: Int
    ): Boolean {
        val files = pending.user.attachments.map { HermesFileLoader.load(it, attachmentRoots.roots()) }
        val content = buildHermesUserContent(pending.user.kind, pending.user.text, files)
        val history = HermesThreadReducer.priorHistory(threads.snapshot.value, pending)
        val body = chatCompletionBody(route.modelId, history, content)
        var accepted = false
        var terminal = false
        return try {
            transport.streamChat(
                route = route,
                token = token,
                sessionId = pending.threadId,
                sessionKey = "${HermesDefaults.SESSION_PREFIX}:${pending.threadId}",
                idempotencyKey = pending.assistant.idempotencyKey,
                body = body,
                onAccepted = {
                    accepted = true
                    threads.update { HermesThreadReducer.markAccepted(it, pending.assistant.id) }
                },
                onEvent = { event ->
                    if (event is HermesStreamEvent.Completed || event is HermesStreamEvent.Failed) terminal = true
                    threads.update { HermesThreadReducer.applyEvent(it, pending.assistant.id, event) }
                }
            )
            if (!terminal) {
                threads.update {
                    HermesThreadReducer.applyEvent(it, pending.assistant.id, HermesStreamEvent.Completed(null))
                }
            }
            false
        } catch (error: HermesHttpException) {
            when {
                accepted -> {
                    threads.update { HermesThreadReducer.markInterrupted(it, pending.assistant.id) }
                    false
                }
                error.retryable && attempt < HermesDefaults.MAX_AUTO_ATTEMPTS - 1 -> {
                    threads.update { HermesThreadReducer.markQueued(it, pending.assistant.id) }
                    true
                }
                else -> {
                    val message = if (error.code == 401 || error.code == 403) {
                        "Hermes rejected the API key."
                    } else {
                        error.message ?: "Hermes rejected the message."
                    }
                    threads.update { HermesThreadReducer.markFailed(it, pending.assistant.id, message) }
                    false
                }
            }
        } catch (_: HermesNetworkException) {
            when {
                accepted -> {
                    threads.update { HermesThreadReducer.markInterrupted(it, pending.assistant.id) }
                    false
                }
                attempt >= HermesDefaults.MAX_AUTO_ATTEMPTS - 1 -> {
                    threads.update { HermesThreadReducer.markFailed(it, pending.assistant.id, "Could not reach Hermes.") }
                    false
                }
                else -> {
                    threads.update { HermesThreadReducer.markQueued(it, pending.assistant.id) }
                    true
                }
            }
        }
    }

    private suspend fun resolveRoute(settings: HermesSettings, token: String): HermesResolvedRoute? {
        val parsed = parseHermesEndpoint(settings.endpoint) ?: return null
        val profile = settings.profile.ifBlank { HermesDefaults.PROFILE }
        if (!isValidHermesProfile(profile)) return null
        if (settings.routeMode != HermesRouteMode.UNKNOWN && settings.modelId.isNotBlank()) {
            return routeFor(parsed, profile, settings.routeMode, settings.modelId)
        }
        return when (val probe = transport.probe(settings.endpoint, profile, token)) {
            is HermesProbeResult.Success -> {
                settingsStore.update {
                    it.copy(routeMode = probe.mode, modelId = probe.modelId, streaming = probe.streaming)
                }
                routeFor(parsed, profile, probe.mode, probe.modelId)
            }
            else -> null
        }
    }

    private fun routeFor(
        parsed: HermesEndpoint,
        profile: String,
        mode: HermesRouteMode,
        modelId: String
    ): HermesResolvedRoute = HermesResolvedRoute(
        apiRoot = if (mode == HermesRouteMode.MULTIPLEX) multiplexApiRoot(parsed.base, profile) else directApiRoot(parsed.base),
        mode = mode,
        modelId = hermesModelId(profile, modelId)
    )

    private suspend fun failQueued(message: String) {
        threads.update { snapshot ->
            snapshot.copy(
                threads = snapshot.threads.map { thread ->
                    thread.copy(
                        turns = thread.turns.map { turn ->
                            if (turn.role == HermesRole.ASSISTANT &&
                                (turn.status == HermesDeliveryStatus.QUEUED || turn.status == HermesDeliveryStatus.SENDING)
                            ) {
                                turn.copy(status = HermesDeliveryStatus.FAILED, error = message)
                            } else {
                                turn
                            }
                        }
                    )
                }
            )
        }
    }
}
