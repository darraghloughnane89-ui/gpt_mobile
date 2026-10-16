package dev.chungjungsoo.gptmobile.data.oauth

import dev.chungjungsoo.gptmobile.data.model.ClientType
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Sign-in is implemented only where a public client can complete it.
 *
 * OpenRouter PKCE exchanges an authorization code for a user API key. There is no refresh token.
 * https://openrouter.ai/docs/guides/overview/auth/oauth
 *
 * xAI uses the public PKCE client documented by Hermes Agent (auth.x.ai, client
 * b1a00492-073a-47ea-816f-4c329264a828), including plan=generic and echoing code_challenge
 * on the token request. Access tokens refresh with offline_access. A SuperGrok or X Premium+
 * subscription may still be rejected with HTTP 403; the API key path remains available.
 *
 * Google Gemini chat is key-only. Google's documented OAuth scopes cover tuning and semantic
 * retrieval and require an app-specific OAuth client this build does not ship.
 * OpenAI, Anthropic, Groq, Ollama, MiMo, and custom endpoints are key-only as well.
 */
enum class ProviderSignInKind {
    KEY_ONLY,
    OPENROUTER_PKCE,
    XAI_AUTHORIZATION_CODE
}

fun ClientType.signInKind(): ProviderSignInKind = when (this) {
    ClientType.OPENROUTER -> ProviderSignInKind.OPENROUTER_PKCE
    ClientType.XAI -> ProviderSignInKind.XAI_AUTHORIZATION_CODE
    else -> ProviderSignInKind.KEY_ONLY
}

fun ClientType.supportsProviderSignIn(): Boolean = signInKind() != ProviderSignInKind.KEY_ONLY

object Pkce {
    private val random = SecureRandom()

    fun verifier(): String {
        val bytes = ByteArray(64)
        random.nextBytes(bytes)
        return base64Url(bytes).take(96)
    }

    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

data class OAuthCallback(
    val code: String?,
    val error: String?
)

object LoopbackOAuthRequests {
    fun parseRequestLine(requestLine: String, expectedPath: String): OAuthCallback? {
        val parts = requestLine.trim().split(" ")
        if (parts.size < 2 || !parts[0].equals("GET", ignoreCase = true)) return null
        val target = parts[1]
        val path = target.substringBefore("?")
        if (path != expectedPath) return null
        val query = target.substringAfter("?", "")
        val params = query.split("&").mapNotNull { pair ->
            val name = pair.substringBefore("=").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val value = java.net.URLDecoder.decode(pair.substringAfter("=", ""), Charsets.UTF_8.name())
            name to value
        }.toMap()
        return OAuthCallback(code = params["code"]?.takeIf { it.isNotBlank() }, error = params["error"]?.takeIf { it.isNotBlank() })
    }
}
