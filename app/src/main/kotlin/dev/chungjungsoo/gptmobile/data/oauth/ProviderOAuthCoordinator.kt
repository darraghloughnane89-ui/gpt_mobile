package dev.chungjungsoo.gptmobile.data.oauth

import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ProviderSignInStart(
    val clientType: ClientType,
    val redirectUri: String,
    val codeVerifier: String,
    val codeChallenge: String,
    val state: String,
    val nonce: String,
    val authorizeUrl: String
)

@Singleton
class ProviderOAuthCoordinator @Inject constructor(
    private val networkClient: NetworkClient,
    private val sessions: ProviderOAuthSessionStore
) {
    private val mutex = Mutex()
    private var pending: PendingLogin? = null

    suspend fun begin(clientType: ClientType): ProviderSignInStart = mutex.withLock {
        pending?.server?.close()
        val kind = clientType.signInKind()
        if (kind == ProviderSignInKind.KEY_ONLY) error("This provider does not offer sign-in")
        val server = LoopbackOAuthServer()
        server.open()
        val verifier = Pkce.verifier()
        val challenge = Pkce.challenge(verifier)
        val redirect = server.redirectUri
        val state = UUID.randomUUID().toString()
        val nonce = UUID.randomUUID().toString()
        val authorizeUrl = when (kind) {
            ProviderSignInKind.OPENROUTER_PKCE -> OpenRouterOAuth.authorizeUrl(redirect, challenge)
            ProviderSignInKind.XAI_AUTHORIZATION_CODE -> XaiOAuth.authorizeUrl(
                redirectUri = redirect,
                codeChallenge = challenge,
                state = state,
                nonce = nonce
            )

            ProviderSignInKind.KEY_ONLY -> error("This provider does not offer sign-in")
        }
        pending = PendingLogin(clientType, verifier, challenge, redirect, state, server)
        ProviderSignInStart(
            clientType = clientType,
            redirectUri = redirect,
            codeVerifier = verifier,
            codeChallenge = challenge,
            state = state,
            nonce = nonce,
            authorizeUrl = authorizeUrl
        )
    }

    suspend fun awaitAccessToken(platformUid: String): String {
        val login = mutex.withLock { pending } ?: error("Sign-in has not started")
        return try {
            val callback = login.server.awaitCallback()
            if (!callback.error.isNullOrBlank()) error("Sign-in was declined")
            val code = callback.code ?: error("Sign-in did not return a code")
            exchange(login, code, platformUid)
        } finally {
            login.server.close()
            mutex.withLock {
                if (pending === login) pending = null
            }
        }
    }

    suspend fun exchangePastedCode(platformUid: String, code: String): String {
        val login = mutex.withLock { pending } ?: error("Start sign-in before pasting a code")
        return try {
            exchange(login, code.trim(), platformUid)
        } finally {
            login.server.close()
            mutex.withLock {
                if (pending === login) pending = null
            }
        }
    }

    fun cancel() {
        pending?.server?.close()
        pending = null
    }

    private suspend fun exchange(login: PendingLogin, code: String, platformUid: String): String {
        val http = networkClient()
        return when (login.clientType.signInKind()) {
            ProviderSignInKind.OPENROUTER_PKCE -> {
                val body = http.preparePost(OpenRouterOAuth.KEYS_URL) {
                    contentType(ContentType.Application.Json)
                    setBody(OpenRouterOAuth.exchangeBody(code, login.verifier))
                }.execute { response ->
                    val text = response.bodyAsText()
                    if (!response.status.isSuccess()) error("OpenRouter sign-in failed (${response.status.value})")
                    text
                }
                val key = OpenRouterOAuth.parseKey(body)
                sessions.save(
                    platformUid,
                    ProviderOAuthSession(kind = "openrouter", accessToken = key)
                )
                key
            }

            ProviderSignInKind.XAI_AUTHORIZATION_CODE -> {
                val form = XaiOAuth.authorizationCodeForm(code, login.redirectUri, login.verifier, login.challenge)
                val tokens = postForm(http, XaiOAuth.TOKEN_URL, form, previousRefreshToken = null)
                sessions.save(
                    platformUid,
                    ProviderOAuthSession(
                        kind = "xai",
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresAtEpochMs = tokens.expiresAtEpochMs,
                        tokenEndpoint = XaiOAuth.TOKEN_URL
                    )
                )
                tokens.accessToken
            }

            ProviderSignInKind.KEY_ONLY -> error("This provider does not offer sign-in")
        }
    }

    private data class PendingLogin(
        val clientType: ClientType,
        val verifier: String,
        val challenge: String,
        val redirectUri: String,
        val state: String,
        val server: LoopbackOAuthServer
    )
}

fun interface PlatformTokenRefresher {
    suspend fun refresh(platform: dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2): dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
}

@Singleton
class ProviderOAuthRefresher @Inject constructor(
    private val sessions: ProviderOAuthSessionStore,
    private val networkClient: NetworkClient
) : PlatformTokenRefresher {
    override suspend fun refresh(platform: dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2): dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2 {
        if (platform.compatibleType.signInKind() != ProviderSignInKind.XAI_AUTHORIZATION_CODE) return platform
        val session = sessions.read(platform.uid) ?: return platform
        val refreshToken = session.refreshToken?.takeIf { it.isNotBlank() } ?: return platform
        val expiresAt = session.expiresAtEpochMs ?: return platform
        if (System.currentTimeMillis() + XaiOAuth.REFRESH_SKEW_MS < expiresAt) return platform
        val endpoint = session.tokenEndpoint ?: XaiOAuth.TOKEN_URL
        val tokens = postForm(
            networkClient(),
            endpoint,
            XaiOAuth.refreshForm(refreshToken),
            previousRefreshToken = refreshToken
        )
        sessions.save(
            platform.uid,
            session.copy(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresAtEpochMs = tokens.expiresAtEpochMs
            )
        )
        return platform.copy(token = tokens.accessToken)
    }
}

internal suspend fun postForm(
    http: io.ktor.client.HttpClient,
    url: String,
    fields: Map<String, String>,
    previousRefreshToken: String?
): ParsedOAuthTokens {
    val body = fields.entries.joinToString("&") { (name, value) ->
        java.net.URLEncoder.encode(name, Charsets.UTF_8.name()) + "=" + java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    }
    val responseBody = http.preparePost(url) {
        header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
        setBody(body)
    }.execute { response ->
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) error("Token request failed (${response.status.value})")
        text
    }
    return XaiOAuth.parseTokenResponse(responseBody, System.currentTimeMillis(), previousRefreshToken)
}
