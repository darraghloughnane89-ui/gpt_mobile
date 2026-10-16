package dev.chungjungsoo.gptmobile.data.plugin

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import dev.chungjungsoo.gptmobile.data.agent.tool.McpOAuthCredential
import dev.chungjungsoo.gptmobile.data.agent.tool.oauthScopeExceedsRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest

/**
 * AppAuth authorization-code + PKCE session. Discovery and dynamic client
 * registration stay in [PluginOAuthFlow] because AppAuth does not implement
 * MCP protected-resource metadata or RFC 7591.
 */
object PluginOAuthBrowser {
    fun authorizationRequest(spec: AppAuthLaunchSpec): AuthorizationRequest {
        val configuration = AuthorizationServiceConfiguration(
            Uri.parse(spec.authorizationEndpoint),
            Uri.parse(spec.tokenEndpoint)
        )
        val builder = AuthorizationRequest.Builder(
            configuration,
            spec.clientId,
            ResponseTypeValues.CODE,
            Uri.parse(spec.redirectUri)
        )
            .setCodeVerifier(spec.codeVerifier, spec.codeChallenge, spec.codeChallengeMethod)
            .setState(spec.state)
            .setAdditionalParameters(mapOf("resource" to spec.resource))
        if (spec.scopes.isNotEmpty()) {
            builder.setScopes(spec.scopes)
        }
        return builder.build()
    }

    fun launch(context: Context, spec: AppAuthLaunchSpec) {
        val service = AuthorizationService(context)
        try {
            val intent = service.getAuthorizationRequestIntent(authorizationRequest(spec))
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(spec.authorizationUri))
        } finally {
            service.dispose()
        }
    }

    suspend fun exchange(context: Context, spec: AppAuthLaunchSpec, callbackUri: String): McpOAuthCredential {
        val request = authorizationRequest(spec)
        val response = AuthorizationResponse.Builder(request).fromUri(Uri.parse(callbackUri)).build()
        if (response.state != spec.state) {
            throw IllegalStateException("OAuth callback state did not match.")
        }
        val code = response.authorizationCode ?: throw IllegalStateException("OAuth callback did not include an authorization code.")
        val tokenRequest = TokenRequest.Builder(request.configuration, request.clientId)
            .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
            .setAuthorizationCode(code)
            .setRedirectUri(request.redirectUri)
            .setCodeVerifier(spec.codeVerifier)
            .setAdditionalParameters(mapOf("resource" to spec.resource))
            .build()
        val service = AuthorizationService(context)
        return suspendCancellableCoroutine { continuation ->
            service.performTokenRequest(tokenRequest) { token, exception ->
                service.dispose()
                val access = token?.accessToken?.trim().orEmpty()
                if (oauthScopeExceedsRequest(spec.scopes, token?.scope)) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException("OAuth token scope is wider than the requested scope.")
                        )
                    }
                } else if (access.isNotEmpty()) {
                    val expiresAt = token?.accessTokenExpirationTime?.let { it / 1000 }
                    continuation.resume(
                        McpOAuthCredential(
                            clientId = spec.clientId,
                            tokenEndpoint = spec.tokenEndpoint,
                            resource = spec.resource,
                            accessToken = access,
                            tokenType = token?.tokenType ?: "Bearer",
                            refreshToken = token?.refreshToken,
                            expiresAtEpochSeconds = expiresAt,
                            scope = token?.scope
                        )
                    )
                } else if (continuation.isActive) {
                    continuation.resumeWithException(exception ?: IllegalStateException("OAuth token exchange failed."))
                }
            }
        }
    }
}
