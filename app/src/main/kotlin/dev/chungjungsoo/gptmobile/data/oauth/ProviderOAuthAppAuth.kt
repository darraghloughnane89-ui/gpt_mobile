package dev.chungjungsoo.gptmobile.data.oauth

import android.content.Intent
import android.net.Uri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

/**
 * Browser sign-in goes through AppAuth-Android (Apache-2.0).
 *
 * xAI is a normal authorization-code request. AppAuth builds the PKCE authorize URL
 * and the Custom Tab. The loopback redirect is what auth.x.ai's public client accepts,
 * so the code comes back through [LoopbackOAuthServer] rather than AppAuth's redirect activity.
 * The token POST still echoes code_challenge, which AppAuth's stock token request omits
 * and which auth.x.ai requires.
 *
 * OpenRouter's documented PKCE page is not an OAuth authorization endpoint. It takes
 * callback_url and code_challenge, then the code is exchanged at /api/v1/auth/keys.
 * AppAuth still owns the Custom Tab. The key exchange stays on our HTTP client.
 */
object ProviderOAuthAppAuth {
    fun authorizationIntent(service: AuthorizationService, start: ProviderSignInStart): Intent = when (start.clientType.signInKind()) {
        ProviderSignInKind.XAI_AUTHORIZATION_CODE -> service.getAuthorizationRequestIntent(xaiAuthorizationRequest(start))
        ProviderSignInKind.OPENROUTER_PKCE -> service.createCustomTabsIntentBuilder().build().intent.apply {
            data = Uri.parse(start.authorizeUrl)
        }

        ProviderSignInKind.KEY_ONLY -> error("This provider does not offer sign-in")
    }

    fun xaiAuthorizationRequest(start: ProviderSignInStart): AuthorizationRequest {
        val configuration = AuthorizationServiceConfiguration(
            Uri.parse(XaiOAuth.AUTHORIZE_URL),
            Uri.parse(XaiOAuth.TOKEN_URL)
        )
        return AuthorizationRequest.Builder(
            configuration,
            XaiOAuth.CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(start.redirectUri)
        )
            .setScope(XaiOAuth.SCOPE)
            .setState(start.state)
            .setNonce(start.nonce)
            .setCodeVerifier(start.codeVerifier, start.codeChallenge, AuthorizationRequest.CODE_CHALLENGE_METHOD_S256)
            .setAdditionalParameters(mapOf("plan" to "generic", "referrer" to "gptmobile"))
            .build()
    }
}
