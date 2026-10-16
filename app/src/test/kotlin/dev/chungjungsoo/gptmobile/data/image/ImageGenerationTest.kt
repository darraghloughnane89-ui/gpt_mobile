package dev.chungjungsoo.gptmobile.data.image

import dev.chungjungsoo.gptmobile.data.model.ClientType
import java.io.File
import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageGenerationTest {
    @Test
    fun `detects configured image models and ignores chat models`() {
        assertEquals(
            ImageApiKind.OPENAI,
            ImageModelCapability.detect(ClientType.OPENAI, "https://api.openai.com/v1/", "gpt-image-1")?.kind
        )
        assertEquals(
            ImageApiKind.OPENAI,
            ImageModelCapability.detect(ClientType.OPENAI, "https://api.openai.com/v1/", "dall-e-3")?.kind
        )
        assertEquals(
            ImageApiKind.OPENAI,
            ImageModelCapability.detect(ClientType.CUSTOM, "https://example.com/v1", "chatgpt-image-latest")?.kind
        )
        assertEquals(
            ImageApiKind.XAI,
            ImageModelCapability.detect(ClientType.OPENAI, "https://api.x.ai/v1/", "grok-2-image-1212")?.kind
        )
        assertEquals(
            ImageApiKind.XAI,
            ImageModelCapability.detect(ClientType.CUSTOM, "https://api.x.ai/v1/", "grok-imagine-image")?.kind
        )
        assertEquals(
            ImageApiKind.GEMINI,
            ImageModelCapability.detect(ClientType.GOOGLE, "https://generativelanguage.googleapis.com/v1beta/", "gemini-2.5-flash-image")?.kind
        )
        assertEquals(
            ImageApiKind.IMAGEN,
            ImageModelCapability.detect(ClientType.GOOGLE, "https://generativelanguage.googleapis.com/", "imagen-4.0-generate-001")?.kind
        )
        assertNull(ImageModelCapability.detect(ClientType.OPENAI, "https://api.openai.com/v1/", "gpt-4o"))
        assertNull(ImageModelCapability.detect(ClientType.GOOGLE, "https://generativelanguage.googleapis.com/", "gemini-2.5-flash"))
        assertNull(ImageModelCapability.detect(ClientType.GROQ, "https://api.groq.com/openai/v1/", "grok-2-image"))
        assertNull(ImageModelCapability.detect(ClientType.ANTHROPIC, "https://api.anthropic.com/", "claude-image"))
    }

    @Test
    fun `image style command toggles and styles the prompt`() {
        assertTrue(ImageStyleCommands.parse("/image-style") is ImageStyleCommand.Toggle)
        assertTrue(ImageStyleCommands.parse("/image-style off") is ImageStyleCommand.Off)
        assertEquals("watercolor", (ImageStyleCommands.parse("/image-style watercolor") as ImageStyleCommand.Set).style)
        assertNull(ImageStyleCommands.parse("draw a cat"))

        val enabled = ImageStyleCommands.apply(ImageStyleCommand.Set("watercolor"), ImageStylePreference())
        assertEquals("a red cube\n\nStyle: watercolor", ImageStyleCommands.styledPrompt("a red cube", enabled))
        val disabled = ImageStyleCommands.apply(ImageStyleCommand.Off, enabled)
        assertFalse(disabled.enabled)
        assertEquals("a red cube", ImageStyleCommands.styledPrompt("a red cube", disabled))

        val directory = File(System.getProperty("java.io.tmpdir"), "styles-${UUID.randomUUID()}")
        val store = ImageStyleStore(directory)
        store.save(4, enabled)
        assertEquals(enabled, store.load(4))
        directory.deleteRecursively()
    }

    @Test
    fun `saved images stay inside app storage and gallery names stay safe`() {
        val directory = File(System.getProperty("java.io.tmpdir"), "generated-${UUID.randomUUID()}").apply { mkdirs() }
        val attachment = GeneratedImageStore(directory).save(byteArrayOf(1, 2, 3), "image/png")
        val saved = File(attachment.localFilePath)
        assertTrue(saved.isFile)
        assertTrue(saved.canonicalPath.startsWith(directory.canonicalPath))
        assertFalse(saved.name.contains("sk-"))
        assertEquals("secret_sk-key.png", galleryDisplayName("../secret sk-key", "image/png"))
        assertEquals("generated-image.png", galleryDisplayName("...", "image/png"))
        assertEquals(2, inSampleSize(4000, 3000, 1024))
        assertEquals("image/png", sniffImageMime(Base64.getDecoder().decode("iVBORw0KGgo=")))
        saved.delete()
        directory.deleteRecursively()
    }

    @Test
    fun `provider errors redact secrets and drop image payloads`() {
        val secret = "sk-test-secret-value"
        val message = imageHttpError(401, """{"error":{"message":"bad key $secret"}}""", secret)
        assertFalse(message.contains(secret))
        assertTrue(message.contains("[redacted]"))
        val payload = "b64_json " + "a".repeat(120)
        assertFalse(imageHttpError(200, payload, secret).contains("aaa"))
    }
}
