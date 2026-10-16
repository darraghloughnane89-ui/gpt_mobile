package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.data.image.ImageApiKind
import dev.chungjungsoo.gptmobile.data.image.ImageGenerationResult
import dev.chungjungsoo.gptmobile.data.image.ImageGenerationTarget
import io.ktor.client.engine.cio.CIO
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationClientTest {
    private val secret = "sk-test-secret-value"
    private val png = Base64.getEncoder().encodeToString(
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    )

    @Test
    fun `openai gpt-image sends the key only as a bearer header`() = withServer(
        MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"data":[{"b64_json":"$png","revised_prompt":"a red cube"}]}"""
        )
    ) { server, client ->
        val result = client.generate(
            ImageGenerationTarget(ImageApiKind.OPENAI, "gpt-image-1"),
            "a red cube",
            30,
            ProviderRequestConfig(server.url("/v1/").toString(), secret)
        )
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()

        assertEquals("/v1/images/generations", recorded.path)
        assertEquals("Bearer $secret", recorded.getHeader("Authorization"))
        assertFalse(recorded.requestUrl.toString().contains(secret))
        assertFalse(body.contains(secret))
        assertFalse(body.contains("response_format"))
        assertTrue(body.contains("gpt-image-1"))
        val success = result as ImageGenerationResult.Success
        assertEquals("a red cube", success.caption)
        assertEquals("image/png", success.mimeType)
        assertFalse(success.bytes.isEmpty())
    }

    @Test
    fun `dall-e requests base64 bytes`() = withServer(
        MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"data":[{"b64_json":"$png"}]}"""
        )
    ) { server, client ->
        client.generate(
            ImageGenerationTarget(ImageApiKind.OPENAI, "dall-e-3"),
            "a lighthouse",
            30,
            ProviderRequestConfig(server.url("/v1/").toString(), secret)
        )
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"response_format\":\"b64_json\""))
        assertTrue(body.contains("1024x1024"))
    }

    @Test
    fun `xai grok image uses the images endpoint without a query key`() = withServer(
        MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"data":[{"b64_json":"$png"}]}"""
        )
    ) { server, client ->
        val result = client.generate(
            ImageGenerationTarget(ImageApiKind.XAI, "grok-2-image"),
            "a comet",
            30,
            ProviderRequestConfig(server.url("/v1/").toString(), secret)
        )
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()
        assertEquals("/v1/images/generations", recorded.path)
        assertEquals("Bearer $secret", recorded.getHeader("Authorization"))
        assertFalse(recorded.path!!.contains("key="))
        assertTrue(body.contains("\"response_format\":\"b64_json\""))
        assertFalse(body.contains("\"size\""))
        assertTrue(result is ImageGenerationResult.Success)
    }

    @Test
    fun `google gemini image uses the api key header`() = withServer(
        MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"candidates":[{"content":{"parts":[{"text":"done"},{"inlineData":{"mimeType":"image/png","data":"$png"}}]}}]}"""
        )
    ) { server, client ->
        val result = client.generate(
            ImageGenerationTarget(ImageApiKind.GEMINI, "gemini-2.5-flash-image"),
            "a paper crane",
            30,
            ProviderRequestConfig(server.url("/v1beta/").toString(), secret)
        )
        val recorded = server.takeRequest()
        assertEquals("/v1beta/models/gemini-2.5-flash-image:generateContent", recorded.path)
        assertEquals(secret, recorded.getHeader("x-goog-api-key"))
        assertFalse(recorded.requestUrl.toString().contains(secret))
        assertFalse(recorded.body.readUtf8().contains(secret))
        val success = result as ImageGenerationResult.Success
        assertEquals("done", success.caption)
        assertEquals("image/png", success.mimeType)
    }

    @Test
    fun `google imagen reads bytesBase64Encoded`() = withServer(
        MockResponse().setHeader("Content-Type", "application/json").setBody(
            """{"predictions":[{"bytesBase64Encoded":"$png","mimeType":"image/png"}]}"""
        )
    ) { server, client ->
        val result = client.generate(
            ImageGenerationTarget(ImageApiKind.IMAGEN, "imagen-4.0-generate-001"),
            "a canyon",
            30,
            ProviderRequestConfig(server.url("/v1beta/").toString(), secret)
        )
        val recorded = server.takeRequest()
        assertEquals("/v1beta/models/imagen-4.0-generate-001:predict", recorded.path)
        assertEquals(secret, recorded.getHeader("x-goog-api-key"))
        assertTrue(result is ImageGenerationResult.Success)
    }

    @Test
    fun `image links are downloaded without the api key`() {
        val server = MockWebServer()
        val image = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        server.start()
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"data":[{"url":"${server.url("/file.png")}"}]}"""
            )
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(image)))
        val network = NetworkClient(CIO)
        try {
            val result = runBlocking {
                ImageGenerationClientImpl(network).generate(
                    ImageGenerationTarget(ImageApiKind.OPENAI, "dall-e-2"),
                    "a map",
                    30,
                    ProviderRequestConfig(server.url("/v1/").toString(), secret)
                )
            }
            val generation = server.takeRequest()
            val download = server.takeRequest()
            assertEquals("/v1/images/generations", generation.path)
            assertEquals("/file.png", download.path)
            assertEquals(null, download.getHeader("Authorization"))
            assertEquals(null, download.getHeader("x-goog-api-key"))
            assertTrue(result is ImageGenerationResult.Success)
        } finally {
            network().close()
            server.shutdown()
        }
    }

    @Test
    fun `http errors do not return the api key`() = withServer(
        MockResponse().setResponseCode(401).setHeader("Content-Type", "application/json").setBody(
            """{"error":{"message":"rejected $secret"}}"""
        )
    ) { server, client ->
        val result = client.generate(
            ImageGenerationTarget(ImageApiKind.OPENAI, "gpt-image-1"),
            "a red cube",
            30,
            ProviderRequestConfig(server.url("/v1/").toString(), secret)
        ) as ImageGenerationResult.Failure
        assertFalse(result.message.contains(secret))
        assertTrue(result.message.contains("[redacted]"))
        server.takeRequest()
    }

    private fun withServer(
        response: MockResponse,
        block: suspend (MockWebServer, ImageGenerationClientImpl) -> Unit
    ) {
        val server = MockWebServer()
        server.enqueue(response)
        server.start()
        val network = NetworkClient(CIO)
        try {
            runBlocking {
                block(server, ImageGenerationClientImpl(network))
            }
        } finally {
            network().close()
            server.shutdown()
        }
    }
}
