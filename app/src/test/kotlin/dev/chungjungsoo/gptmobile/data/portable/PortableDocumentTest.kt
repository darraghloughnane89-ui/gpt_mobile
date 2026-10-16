package dev.chungjungsoo.gptmobile.data.portable

import dev.chungjungsoo.gptmobile.data.bot.BotDraft
import dev.chungjungsoo.gptmobile.data.bot.BotToolNames
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableDocumentTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun exportBot_isPlainJsonWithoutSignatureSecretsOrConnectionIds() {
        val exported = PortableDocuments.exportBot(
            BotDraft(
                name = "Field notes",
                avatarEmoji = "📋",
                avatarColor = "#006A6A",
                instructions = "Write the report. Do not invent a token.",
                defaultPlatformUid = "platform-with-key",
                defaultModel = "gpt-4.1",
                starterPrompts = listOf("Draft the visit"),
                builtin = true,
                currentDate = true,
                readUrl = true,
                webSearchConnectionUid = "search-secret-uid",
                mcpConnectionUids = setOf("mcp-secret-uid")
            )
        )

        assertFalse(exported.contains("signature"))
        assertFalse(exported.contains("search-secret-uid"))
        assertFalse(exported.contains("mcp-secret-uid"))
        assertFalse(exported.contains("platform-with-key"))
        assertFalse(keys(exported).any { it in SECRET_AND_IDENTITY })
        val preview = (PortableDocuments.parse(exported) as PortableResult.Ok).preview as PortablePreview.Bot
        assertEquals("Field notes", preview.body.name)
        assertEquals(listOf("Draft the visit"), preview.body.starterPrompts)
        assertEquals("gpt-4.1", preview.body.defaultModel)
        assertEquals(
            listOf(
                BuiltInAgentTool.CURRENT_DATE to PortableToolDecision.ENABLE,
                BuiltInAgentTool.READ_URL to PortableToolDecision.ENABLE,
                PortablePolicy.WEB_SEARCH to PortableToolDecision.WITHHOLD,
                PortablePolicy.MCP to PortableToolDecision.WITHHOLD
            ),
            preview.tools.map { it.name to it.decision }
        )
        val (bot, bindings) = preview.toEntities("new-id", 10)
        assertFalse(bot.builtin)
        assertNull(bot.defaultPlatformUid)
        assertEquals("gpt-4.1", bot.defaultModel)
        assertEquals(listOf(BuiltInAgentTool.CURRENT_DATE, BuiltInAgentTool.READ_URL), bindings.map { it.toolName })
        assertTrue(bindings.all { it.connectionUid == null })
        assertFalse(bindings.any { it.toolName == BotToolNames.WEB_SEARCH || it.toolName == BotToolNames.MCP_ALL })
    }

    @Test
    fun import_dropsSecretsMemoryIdentityAndIgnoresSignature() {
        val raw = """
            {
              "format": "robin.portable",
              "version": 1,
              "kind": "skill",
              "signature": "forged",
              "item": {
                "name": "Email Drafter",
                "title": "Email Drafter",
                "description": "When a note should become an email.",
                "instructions": "Be helpful.",
                "tools": ["web_search", "read_url"],
                "examples": ["Chase the quote"],
                "token": "sk-live-secret",
                "apiKey": "another-secret",
                "memory": ["home address"],
                "connectionUid": "conn-1",
                "builtin": true,
                "origin": "learned",
                "useCount": 40,
                "pinned": true
              }
            }
        """.trimIndent()

        val preview = (PortableDocuments.parse(raw) as PortableResult.Ok).preview as PortablePreview.Skill
        assertEquals("email-drafter", preview.body.name)
        assertEquals("Be helpful.", preview.body.instructions)
        assertFalse(preview.body.instructions.contains("sk-live-secret"))
        assertFalse(preview.body.instructions.contains("home address"))
        assertTrue(preview.warnings.contains(PortableWarning.STRIPPED_SECRETS))
        assertTrue(preview.warnings.contains(PortableWarning.IGNORED_SIGNATURE))
        assertTrue(preview.warnings.contains(PortableWarning.IGNORED_IDENTITY))
        assertTrue(preview.warnings.contains(PortableWarning.SLUG_ADJUSTED))
        assertTrue(preview.tools.all { it.decision == PortableToolDecision.RECORD_ONLY })
        val stored = preview.toStored("id", 3)
        assertTrue(stored.untrusted)
        assertEquals(listOf("web_search", "read_url"), stored.tools)
        assertFalse(stored.toString().contains("sk-live-secret"))
    }

    @Test
    fun prompt_keepsTextAndDoesNotGrantTools() {
        val raw = """
            {"format":"robin.portable","version":1,"kind":"prompt","item":{"title":"Lift note","text":"Summarise the visit.","tools":["mcp"],"token":"sekret"}}
        """.trimIndent()
        val preview = (PortableDocuments.parse(raw) as PortableResult.Ok).preview as PortablePreview.Prompt
        assertEquals("Summarise the visit.", preview.body.text)
        assertEquals(PortableToolDecision.WITHHOLD, preview.tools.single().decision)
        assertFalse(preview.toStored("p", 1).text.contains("sekret"))
    }

    @Test
    fun deepLink_roundTripsCompressedPayloadAndRejectsKindMismatch() {
        val exported = PortableDocuments.exportSkill(
            PortableSkillBody(
                name = "quoting",
                title = "Quoting",
                description = "When a price is needed.",
                instructions = "Ask for the site, then the parts.",
                tools = listOf("current_date"),
                examples = listOf("Price a door")
            )
        )
        val preview = (PortableDocuments.parse(exported) as PortableResult.Ok).preview
        val link = PortableDocuments.shareLink(preview)
        assertNotNull(link)
        assertTrue(link!!.startsWith("robin://import?p="))
        val payload = link.substringAfter("p=")
        val decoded = PortableDocuments.decodeLink("robin", "import", payload) as PortableResult.Ok
        assertEquals("quoting", (decoded.preview as PortablePreview.Skill).body.name)

        val mismatch = PortableDocuments.decodeLink("robin", "bot", payload)
        assertEquals(PortableFailure.KIND_MISMATCH, (mismatch as PortableResult.Failure).reason)
    }

    @Test
    fun deepLink_rejectsOversizedPayloadAndZipBomb() {
        val hugePayload = "a".repeat(PortableLimits.MAX_LINK_PAYLOAD_CHARS + 1)
        val tooLong = PortableDocuments.decodeLink("robin", "import", hugePayload) as PortableResult.Failure
        assertEquals(PortableFailure.LINK_TOO_LARGE, tooLong.reason)

        val bomb = PortablePayload.compress("A".repeat(PortableLimits.MAX_DOCUMENT_BYTES + 50_000))
        assertTrue(bomb.length < PortableLimits.MAX_LINK_PAYLOAD_CHARS)
        assertTrue(PortablePayload.decompress(bomb) is PortablePayload.Decoded.TooLarge)
    }

    @Test
    fun fileName_mustMatchKind() {
        val bot = PortableDocuments.exportBot(BotDraft(name = "Coder", instructions = "Be careful."))
        val mismatch = PortableDocuments.consider(bot, "coder.robin-skill") as PortableResult.Failure
        assertEquals(PortableFailure.KIND_MISMATCH, mismatch.reason)
        val match = PortableDocuments.consider(bot, "coder.robin-bot") as PortableResult.Ok
        assertEquals(PortableKind.BOT, match.preview.kind)
    }

    @Test
    fun untrustedText_stripsControlsAndBidi() {
        assertEquals("safeevil", untrustedText("safe\u202Eevil\u0000", 100))
        assertEquals("line", untrustedText(" \uFEFFline\r ", 100))
    }

    @Test
    fun rejectsUnknownFormatAndVersion() {
        val unknown = """{"format":"other","version":1,"kind":"bot","item":{"name":"A"}}"""
        assertEquals(
            PortableFailure.NOT_A_DOCUMENT,
            (PortableDocuments.parse(unknown) as PortableResult.Failure).reason
        )
        val version = """{"format":"robin.portable","version":2,"kind":"bot","item":{"name":"A"}}"""
        assertEquals(
            PortableFailure.UNSUPPORTED_VERSION,
            (PortableDocuments.parse(version) as PortableResult.Failure).reason
        )
    }

    @Test
    fun librarySnapshot_roundTripsWithoutSecrets() {
        val snapshot = PortableLibrarySnapshot(
            skills = listOf(
                StoredSkill(
                    id = "s1",
                    name = "quoting",
                    title = "Quoting",
                    description = "Prices",
                    instructions = "Ask first.",
                    tools = listOf("read_url"),
                    untrusted = true,
                    createdAt = 4
                )
            ),
            prompts = listOf(StoredPrompt(id = "p1", title = "Note", text = "Hello", untrusted = true, createdAt = 5)),
            importedBotUids = listOf("bot-1")
        )
        val decoded = PortableLibrarySnapshot.decode(snapshot.encode())
        assertEquals(snapshot, decoded)
        assertFalse(decoded.encode().contains("token"))
    }

    private fun keys(document: String): Set<String> {
        val root = json.parseToJsonElement(document).jsonObject
        val found = mutableSetOf<String>()
        fun walk(value: kotlinx.serialization.json.JsonElement) {
            if (value is JsonObject) {
                value.forEach { (key, child) ->
                    found += key.lowercase().replace("_", "").replace("-", "")
                    walk(child)
                }
            }
        }
        walk(root)
        return found
    }

    private companion object {
        val SECRET_AND_IDENTITY = setOf(
            "token",
            "apikey",
            "secret",
            "memory",
            "connectionuid",
            "defaultplatformuid",
            "signature",
            "builtin"
        )
    }
}
