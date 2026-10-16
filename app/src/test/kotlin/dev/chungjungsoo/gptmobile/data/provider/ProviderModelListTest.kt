package dev.chungjungsoo.gptmobile.data.provider

import dev.chungjungsoo.gptmobile.data.ModelConstants
import dev.chungjungsoo.gptmobile.data.model.ClientType
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderModelListTest {
    @Test
    fun `parser reads OpenAI data, xAI models, aliases, and vision`() {
        val openai = ModelListParser.parse(
            """{"data":[{"id":"gpt-5.4","owned_by":"openai"}]}"""
        )
        assertEquals(listOf("gpt-5.4"), openai.map { it.id })
        assertEquals("openai", openai.single().ownedBy)
        assertFalse(openai.single().supportsVision)

        val xai = ModelListParser.parse(
            """
            {
              "models": [
                {
                  "id": "grok-4.7",
                  "owned_by": "xai",
                  "input_modalities": ["text", "image"],
                  "aliases": ["latest", "grok-latest"]
                }
              ]
            }
            """.trimIndent()
        )
        assertEquals(listOf("grok-4.7", "grok-latest"), xai.map { it.id })
        assertTrue(xai.all { it.supportsVision })
    }

    @Test
    fun `parser reads Anthropic, Google, and Ollama names`() {
        val anthropic = ModelListParser.parse("""{"data":[{"id":"claude-sonnet-5","type":"model"}]}""")
        assertEquals("claude-sonnet-5", anthropic.single().id)

        val google = ModelListParser.parse("""{"models":[{"name":"models/gemini-2.5-flash"}]}""")
        assertEquals("gemini-2.5-flash", google.single().id)

        val ollama = ModelListParser.parse("""{"models":[{"name":"llama3.2"}]}""")
        assertEquals("llama3.2", ollama.single().id)
    }

    @Test
    fun `parser returns nothing for empty or invalid payloads`() {
        assertTrue(ModelListParser.parse("{}").isEmpty())
        assertTrue(ModelListParser.parse("not-json").isEmpty())
        assertTrue(ModelListParser.parse("""{"data":[{"id":"latest"},{"id":" "}]}""").isEmpty())
    }

    @Test
    fun `vision ids are marked even without modalities`() {
        val models = ModelListParser.parse("""{"data":[{"id":"grok-2-vision"}]}""")
        assertTrue(models.single().supportsVision)
    }

    @Test
    fun `xAI preset uses the documented base url and flagship model`() {
        assertEquals("https://api.x.ai/v1/", ModelConstants.XAI_API_URL)
        assertEquals("grok-4.7", ModelConstants.XAI_DEFAULT_MODEL)
        assertEquals("https://api.x.ai/v1/", ModelConstants.defaultApiUrl(ClientType.XAI))
        assertEquals("grok-4.7", ModelConstants.defaultModel(ClientType.XAI))
        assertTrue(ModelConstants.builtinModelIds(ClientType.XAI).contains("grok-4.3"))
        assertEquals("Grok", ModelConstants.defaultPlatformName(ClientType.XAI))
    }

    @Test
    fun `compatible base urls gain a v1 suffix without rewriting custom paths`() {
        assertEquals("https://api.x.ai/v1/", OpenAiCompatiblePresets.normalizeBaseUrl("https://api.x.ai"))
        assertEquals("https://api.x.ai/v1/", OpenAiCompatiblePresets.normalizeBaseUrl("https://api.x.ai/v1"))
        assertEquals("https://api.x.ai/v1/", OpenAiCompatiblePresets.normalizeBaseUrl("https://api.x.ai/v1/"))
        assertEquals("http://127.0.0.1:1234/v1/", OpenAiCompatiblePresets.normalizeBaseUrl("http://127.0.0.1:1234"))
        assertEquals("http://example.com/custom/path/", OpenAiCompatiblePresets.normalizeBaseUrl("http://example.com/custom/path"))
        assertEquals("not a url", OpenAiCompatiblePresets.normalizeBaseUrl("not a url"))
        assertEquals("", OpenAiCompatiblePresets.normalizeBaseUrl("  "))
        assertTrue(OpenAiCompatiblePresets.shortcuts.any { it.label == "LM Studio" && it.apiUrl.contains("1234") })
    }

    @Test
    fun `model list cache round trips and isolates providers`() {
        val directory = File(System.getProperty("java.io.tmpdir"), "model-list-cache-${System.nanoTime()}")
        val cache = ModelListCache(directory)
        val stored = CachedModelList(
            fetchedAtEpochMs = 10L,
            models = listOf(RemoteModel(id = "grok-4.7", supportsVision = true))
        )
        cache.write(ClientType.XAI.name, ModelConstants.XAI_API_URL, stored)

        assertEquals(stored, cache.read(ClientType.XAI.name, ModelConstants.XAI_API_URL))
        assertNull(cache.read(ClientType.GROQ.name, ModelConstants.XAI_API_URL))
        directory.deleteRecursively()
    }

    @Test
    fun `model list endpoints match each provider`() {
        val http = HttpClient(CIO)
        val client = ModelListClient(http)
        assertEquals(
            "https://api.openai.com/v1/models",
            client.endpoint(ClientType.OPENAI, "https://api.openai.com")
        )
        assertEquals(
            "https://api.x.ai/v1/models",
            client.endpoint(ClientType.XAI, "https://api.x.ai/v1/")
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models",
            client.endpoint(ClientType.GOOGLE, "https://generativelanguage.googleapis.com/")
        )
        assertEquals(
            "https://api.anthropic.com/v1/models",
            client.endpoint(ClientType.ANTHROPIC, "https://api.anthropic.com/v1/")
        )
        assertEquals(
            "http://localhost:11434/v1/models",
            client.endpoint(ClientType.OLLAMA, "http://localhost:11434")
        )
        assertEquals("", client.endpoint(ClientType.CUSTOM, "  "))
        assertEquals("", client.endpoint(ClientType.LLAMA_CPP, "http://127.0.0.1:8080/v1/"))
        http.close()
    }
}
