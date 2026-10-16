package dev.chungjungsoo.gptmobile.data.oauth

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.oauth.signInKind
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import dev.chungjungsoo.gptmobile.data.speech.MiMoSpeechModels
import dev.chungjungsoo.gptmobile.data.speech.MiMoSpeechRequests
import dev.chungjungsoo.gptmobile.data.speech.SpeechSynthesisRequest
import dev.chungjungsoo.gptmobile.data.speech.SpeechTranscriptionRequest
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderOAuthAndSpeechTest {
    @Test
    fun `only OpenRouter and xAI offer sign-in`() {
        assertEquals(ProviderSignInKind.OPENROUTER_PKCE, ClientType.OPENROUTER.signInKind())
        assertEquals(ProviderSignInKind.XAI_AUTHORIZATION_CODE, ClientType.XAI.signInKind())
        assertEquals(ProviderSignInKind.KEY_ONLY, ClientType.GOOGLE.signInKind())
        assertEquals(ProviderSignInKind.KEY_ONLY, ClientType.MIMO.signInKind())
        assertEquals(ProviderSignInKind.KEY_ONLY, ClientType.OPENAI.signInKind())
        assertEquals(ProviderSignInKind.KEY_ONLY, ClientType.ANTHROPIC.signInKind())
    }

    @Test
    fun `xAI authorize url and token form follow the Hermes PKCE shape`() {
        val url = XaiOAuth.authorizeUrl(
            redirectUri = "http://127.0.0.1:9/callback/nonce",
            codeChallenge = "challenge",
            state = "state",
            nonce = "nonce"
        )
        assertTrue(url.startsWith("https://auth.x.ai/oauth2/authorize?"))
        assertTrue(url.contains("client_id=${XaiOAuth.CLIENT_ID}"))
        assertTrue(url.contains("plan=generic"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("grok-cli%3Aaccess") || url.contains("grok-cli:access"))

        val form = XaiOAuth.authorizationCodeForm("code", "http://127.0.0.1/callback", "verifier", "challenge")
        assertEquals("authorization_code", form["grant_type"])
        assertEquals("challenge", form["code_challenge"])
        assertEquals("S256", form["code_challenge_method"])
        assertEquals("verifier", form["code_verifier"])
        assertEquals(XaiOAuth.CLIENT_ID, form["client_id"])
    }

    @Test
    fun `token response keeps the previous refresh token when a new one is absent`() {
        val parsed = XaiOAuth.parseTokenResponse(
            """{"access_token":"access","expires_in":3600}""",
            nowEpochMs = 1_000L,
            previousRefreshToken = "refresh"
        )
        assertEquals("access", parsed.accessToken)
        assertEquals("refresh", parsed.refreshToken)
        assertEquals(3_601_000L, parsed.expiresAtEpochMs)
    }

    @Test
    fun `OpenRouter exchange body and key parser`() {
        val body = OpenRouterOAuth.exchangeBody("auth code", "verifier")
        assertTrue(body.contains(""""code":"auth code""""))
        assertTrue(body.contains(""""code_challenge_method":"S256""""))
        assertEquals("sk-or-v1-test", OpenRouterOAuth.parseKey("""{"key":"sk-or-v1-test","user_id":"u"}"""))
        assertTrue(OpenRouterOAuth.authorizeUrl("http://127.0.0.1:1/callback/n", "chal").contains("callback_url="))
    }

    @Test
    fun `pkce challenge is the sha256 digest and loopback ignores the wrong path`() {
        val challenge = Pkce.challenge("verifier")
        assertEquals(43, challenge.length)
        assertFalse(challenge.contains("="))
        val callback = LoopbackOAuthRequests.parseRequestLine(
            "GET /callback/nonce?code=abc&error= HTTP/1.1",
            "/callback/nonce"
        )
        assertEquals("abc", callback?.code)
        assertNull(LoopbackOAuthRequests.parseRequestLine("GET /other?code=abc HTTP/1.1", "/callback/nonce"))
    }

    @Test
    fun `loopback server returns the authorization code`() = runBlocking {
        val server = LoopbackOAuthServer()
        server.open()
        val pending = async(Dispatchers.IO) { server.awaitCallback(5_000) }
        val connection = URL(server.redirectUri + "?code=from-browser").openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        connection.inputStream.use { it.readBytes() }
        assertEquals("from-browser", pending.await().code)
        server.close()
    }

    @Test
    fun `oauth session round trips through the vault`() = runBlocking {
        val vault = MemoryVault()
        val store = ProviderOAuthSessionStore(vault)
        store.save(
            "platform",
            ProviderOAuthSession(kind = "xai", accessToken = "access", refreshToken = "refresh", expiresAtEpochMs = 5L, tokenEndpoint = XaiOAuth.TOKEN_URL)
        )
        val read = store.read("platform")
        assertEquals("refresh", read?.refreshToken)
        store.clear("platform")
        assertNull(store.read("platform"))
    }

    @Test
    fun `MiMo preset uses the Europe token plan and the documented chat models`() {
        assertEquals("https://token-plan-ams.xiaomimimo.com/v1/", ModelConstants.MIMO_API_URL)
        assertEquals("mimo-v2.6-pro", ModelConstants.defaultModel(ClientType.MIMO))
        assertEquals(
            setOf("mimo-v2.6-pro", "mimo-v2.6-flash", "mimo-v2.5-pro"),
            ModelConstants.builtinModelIds(ClientType.MIMO)
        )
    }

    @Test
    fun `MiMo speech requests use the documented ASR and TTS model ids`() {
        val asr = MiMoSpeechRequests.transcriptionBody(
            SpeechTranscriptionRequest(audioBase64 = "AAAA", mimeType = "audio/wav", language = "en")
        )
        assertTrue(asr.contains(MiMoSpeechModels.ASR))
        assertTrue(asr.contains("input_audio"))
        assertTrue(asr.contains(""""language":"en""""))

        val tts = MiMoSpeechRequests.synthesisBody(
            SpeechSynthesisRequest(text = "Hello", style = "Bright", voice = MiMoSpeechModels.DEFAULT_VOICE)
        )
        assertTrue(tts.contains(MiMoSpeechModels.TTS))
        assertTrue(tts.contains(""""role":"assistant""""))
        assertTrue(tts.contains(MiMoSpeechModels.DEFAULT_VOICE))

        val design = MiMoSpeechRequests.synthesisBody(
            SpeechSynthesisRequest(text = "Hello", modelId = MiMoSpeechModels.TTS_VOICE_DESIGN, voice = "ignored")
        )
        assertTrue(design.contains(MiMoSpeechModels.TTS_VOICE_DESIGN))
        assertFalse(design.contains("ignored"))

        val transcript = MiMoSpeechRequests.parseTranscript(
            """{"choices":[{"message":{"role":"assistant","content":"hello there"}}]}"""
        )
        assertEquals("hello there", transcript)
        assertEquals(
            "QQ==",
            MiMoSpeechRequests.parseAudioBase64("""{"choices":[{"message":{"audio":{"data":"QQ=="}}}]}""")
        )
    }

    private class MemoryVault : SecretVault {
        private val values = mutableMapOf<String, ByteArray>()

        override suspend fun put(secretRef: String, secret: ByteArray) {
            values[secretRef] = secret.copyOf()
        }

        override suspend fun read(secretRef: String): ByteArray? = values[secretRef]?.copyOf()

        override suspend fun delete(secretRef: String) {
            values.remove(secretRef)
        }
    }
}
