package dev.chungjungsoo.gptmobile.data.agent.tool

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSdkMockWebServerTest {
    @Test
    fun `official SDK streamable HTTP client lists and calls tools`() = runBlocking {
        val server = MockWebServer()
        val seenAuthorization = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                request.getHeader("Authorization")?.let(seenAuthorization::add)
                val raw = request.body.readUtf8()
                if (raw.isBlank()) return MockResponse().setResponseCode(202)
                val json = JSON.parseToJsonElement(raw) as JsonObject
                val method = json["method"]?.jsonPrimitive?.content.orEmpty()
                val id = json["id"]
                if (method == "notifications/initialized" || id == null) {
                    return MockResponse().setResponseCode(202)
                }
                val result = when (method) {
                    "initialize" ->
                        """{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"fixture","version":"1"}}"""

                    "tools/list" ->
                        """{"tools":[{"name":"echo","description":"Echo text","inputSchema":{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}}]}"""

                    "tools/call" -> {
                        val text = (json["params"] as JsonObject)["arguments"]
                            .let { it as JsonObject }["text"]!!
                            .jsonPrimitive
                            .content
                        """{"content":[{"type":"text","text":"$text"}]}"""
                    }

                    else -> return MockResponse().setResponseCode(400).setBody(raw)
                }
                val response = MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"jsonrpc":"2.0","id":$id,"result":$result}""")
                if (method == "initialize") {
                    response.setHeader("Mcp-Session-Id", "session-1")
                }
                return response
            }
        }
        server.start()
        val httpClient = testClient()
        val manager = McpClientManager(httpClient)
        val config = McpConnectionConfig(
            connectionUid = "plugin-gmail",
            endpointUrl = server.url("/mcp").toString(),
            allowCleartext = true,
            authorizationHeader = "Bearer user-token",
            transport = McpClientTransport.STREAMABLE_HTTP
        )
        try {
            val tools = manager.listTools(config)
            val result = manager.callTool(
                config,
                "echo",
                buildJsonObject {
                    put("text", "hello")
                }
            )
            assertEquals(listOf("echo"), tools.map { it.name })
            val text = result.content.single() as io.modelcontextprotocol.kotlin.sdk.types.TextContent
            assertEquals("hello", text.text)
            assertTrue(seenAuthorization.all { it == "Bearer user-token" })
        } finally {
            manager.closeAll()
            httpClient.close()
            server.shutdown()
        }
    }

    private fun testClient(): HttpClient = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) { json(JSON) }
        install(SSE)
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
