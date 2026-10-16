package dev.chungjungsoo.gptmobile.data.voice

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiMoSpeechClientTest {
    @Test
    fun `tts streams pcm from the completions endpoint and keeps the key out of errors`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newSingleThreadExecutor()
        server.executor = executor
        var apiKey = ""
        var authorization = ""
        var body = ""
        server.createContext("/v1/chat/completions") { exchange ->
            apiKey = exchange.requestHeaders.getFirst("api-key").orEmpty()
            authorization = exchange.requestHeaders.getFirst("Authorization").orEmpty()
            body = exchange.requestBody.readBytes().decodeToString()
            val payload = if (body.contains("mimo-v2.5-asr")) {
                """{"choices":[{"message":{"content":"heard you"}}]}"""
            } else if (body.contains("fail-please")) {
                """{"error":{"message":"rejected tp-live-key"}}"""
            } else {
                "data: {\"choices\":[{\"delta\":{\"audio\":{\"data\":\"AQI=\"}}}]}\n\ndata: [DONE]\n\n"
            }
            val bytes = payload.toByteArray()
            val status = if (body.contains("fail-please")) 401 else 200
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val port = server.address.port
        val http = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout)
        }
        try {
            val client = MiMoSpeechClient(http, "http://127.0.0.1:$port/v1")
            val audio = client.synthesize("tp-live-key", "Hello", "Mia").toList()
            assertEquals("tp-live-key", apiKey)
            assertEquals("Bearer tp-live-key", authorization)
            assertTrue(body.contains("\"model\":\"mimo-v2.5-tts\""))
            assertTrue(body.contains("pcm16"))
            assertEquals(1, audio.size)
            assertTrue(audio[0].contentEquals(byteArrayOf(1, 2)))

            val transcript = client.transcribe("tp-live-key", byteArrayOf(1, 2, 3, 4))
            assertEquals("heard you", transcript)
            assertTrue(body.contains("mimo-v2.5-asr"))
            assertTrue(body.contains("input_audio"))

            try {
                client.synthesize("tp-live-key", "fail-please", "Mia").toList()
                throw AssertionError("expected MiMo to fail")
            } catch (error: MiMoSpeechException) {
                assertTrue(error.message!!.contains("***"))
                assertFalse(error.message!!.contains("tp-live-key"))
            }
        } finally {
            http.close()
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
