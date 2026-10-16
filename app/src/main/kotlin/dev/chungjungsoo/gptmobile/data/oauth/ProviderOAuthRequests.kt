package dev.chungjungsoo.gptmobile.data.oauth

import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object XaiOAuth {
    const val CLIENT_ID = "b1a00492-073a-47ea-816f-4c329264a828"
    const val AUTHORIZE_URL = "https://auth.x.ai/oauth2/authorize"
    const val TOKEN_URL = "https://auth.x.ai/oauth2/token"
    const val SCOPE = "openid profile email offline_access grok-cli:access api:access"
    const val REFRESH_SKEW_MS = 60 * 60 * 1000L

    /**
     * Same fields as the AppAuth authorization request opened on device.
     * Kept here so the query can be checked without Android.
     */
    fun authorizeUrl(
        redirectUri: String,
        codeChallenge: String,
        state: String,
        nonce: String
    ): String {
        val params = linkedMapOf(
            "response_type" to "code",
            "client_id" to CLIENT_ID,
            "redirect_uri" to redirectUri,
            "scope" to SCOPE,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
            "state" to state,
            "nonce" to nonce,
            "plan" to "generic",
            "referrer" to "gptmobile"
        )
        return AUTHORIZE_URL + "?" + form(params)
    }

    fun authorizationCodeForm(
        code: String,
        redirectUri: String,
        codeVerifier: String,
        codeChallenge: String
    ): Map<String, String> = linkedMapOf(
        "grant_type" to "authorization_code",
        "code" to code,
        "redirect_uri" to redirectUri,
        "client_id" to CLIENT_ID,
        "code_verifier" to codeVerifier,
        "code_challenge" to codeChallenge,
        "code_challenge_method" to "S256"
    )

    fun refreshForm(refreshToken: String): Map<String, String> = linkedMapOf(
        "grant_type" to "refresh_token",
        "refresh_token" to refreshToken,
        "client_id" to CLIENT_ID
    )

    fun parseTokenResponse(body: String, nowEpochMs: Long, previousRefreshToken: String?): ParsedOAuthTokens {
        val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
        val access = root["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: error("xAI token response did not include an access token")
        val refresh = root["refresh_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: previousRefreshToken
        val expiresIn = root["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        val expiresAt = expiresIn?.let { nowEpochMs + it * 1000L }
        return ParsedOAuthTokens(accessToken = access, refreshToken = refresh, expiresAtEpochMs = expiresAt)
    }

    private fun form(params: Map<String, String>): String = params.entries.joinToString("&") { (name, value) ->
        encode(name) + "=" + encode(value)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

object OpenRouterOAuth {
    const val AUTHORIZE_URL = "https://openrouter.ai/auth"
    const val KEYS_URL = "https://openrouter.ai/api/v1/auth/keys"

    fun authorizeUrl(redirectUri: String, codeChallenge: String): String {
        val query = listOf(
            "callback_url" to redirectUri,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256"
        ).joinToString("&") { (name, value) ->
            URLEncoder.encode(name, Charsets.UTF_8.name()) + "=" + URLEncoder.encode(value, Charsets.UTF_8.name())
        }
        return "$AUTHORIZE_URL?$query"
    }

    fun exchangeBody(code: String, codeVerifier: String): String = """{"code":${jsonString(code)},"code_verifier":${jsonString(codeVerifier)},"code_challenge_method":"S256"}"""

    fun parseKey(body: String): String {
        val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
        return root["key"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: error("OpenRouter did not return an API key")
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                else -> append(char)
            }
        }
        append('"')
    }
}

data class ParsedOAuthTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtEpochMs: Long?
)
