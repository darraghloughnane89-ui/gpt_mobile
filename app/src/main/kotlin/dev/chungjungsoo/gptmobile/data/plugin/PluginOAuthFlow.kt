package dev.chungjungsoo.gptmobile.data.plugin

import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthClient
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthCredential
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthDiscovery
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthPending
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthStart
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.Serializable

@Serializable
data class AppAuthLaunchSpec(
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val clientId: String,
    val redirectUri: String,
    val state: String,
    val codeVerifier: String,
    val codeChallenge: String,
    val codeChallengeMethod: String,
    val resource: String,
    val scopes: List<String>,
    val authorizationUri: String
)

class PluginOAuthFlow(
    private val oauthClient: McpOAuthClient
) {
    suspend fun begin(
        resourceUrl: String,
        allowCleartext: Boolean,
        redirectUri: String,
        suppliedClientId: String?
    ): Pair<McpOAuthDiscovery, McpOAuthStart> {
        val discovery = oauthClient.discover(resourceUrl, allowCleartext)
        val start = oauthClient.beginAuthorization(discovery, redirectUri, suppliedClientId)
        return discovery to start
    }

    fun launchSpec(discovery: McpOAuthDiscovery, start: McpOAuthStart): AppAuthLaunchSpec {
        val challenge = start.pending.codeVerifier.sha256Base64Url()
        return AppAuthLaunchSpec(
            authorizationEndpoint = discovery.authorizationEndpoint,
            tokenEndpoint = discovery.tokenEndpoint,
            clientId = start.pending.clientId,
            redirectUri = start.pending.redirectUri,
            state = start.pending.state,
            codeVerifier = start.pending.codeVerifier,
            codeChallenge = challenge,
            codeChallengeMethod = "S256",
            resource = discovery.resource,
            scopes = discovery.scopes,
            authorizationUri = start.authorizationUri
        )
    }

    suspend fun complete(pending: McpOAuthPending, callbackUri: String): McpOAuthCredential = oauthClient.completeAuthorization(pending, callbackUri)

    fun bearerHeader(token: String): String {
        val trimmed = token.trim()
        require(trimmed.isNotEmpty()) { "Bearer token is required." }
        require('\r' !in trimmed && '\n' !in trimmed) { "Bearer token is invalid." }
        require(!trimmed.startsWith("Bearer ", ignoreCase = true)) { "Paste the token without a Bearer prefix." }
        return "Bearer $trimmed"
    }
}

fun pluginOAuthRedirectUri(pluginUid: String): String {
    require(PLUGIN_UID_PATTERN.matches(pluginUid)) { "Plugin id is invalid." }
    return "dev.chungjungsoo.gptmobile://oauth/plugin/$pluginUid"
}

fun pluginOAuthPluginUid(callbackUri: String): String? = runCatching { java.net.URI(callbackUri) }
    .getOrNull()
    ?.takeIf { it.scheme == "dev.chungjungsoo.gptmobile" && it.host == "oauth" && it.path?.startsWith("/plugin/") == true }
    ?.path
    ?.substringAfterLast('/')
    ?.takeIf(PLUGIN_UID_PATTERN::matches)

fun isPluginOAuthCallback(callbackUri: String?): Boolean = callbackUri?.let(::pluginOAuthPluginUid) != null

private fun String.sha256Base64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
    MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
)

private val PLUGIN_UID_PATTERN = Regex("[A-Za-z0-9_-]{1,96}")
