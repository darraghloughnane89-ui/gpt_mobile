package dev.chungjungsoo.gptmobile.data.bot

import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.data.database.entity.BotToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotDraftTest {
    @Test
    fun sanitized_trimsNameAndDropsBlankStarterPrompts() {
        val draft = BotDraft(
            name = "  Coder  ",
            avatarEmoji = " 💻 ",
            avatarColor = "not-a-color",
            instructions = "  Be careful.  ",
            starterPrompts = listOf(" Review this ", "", "Review this", "x".repeat(250))
        ).sanitized()

        assertEquals("Coder", draft.name)
        assertEquals("💻", draft.avatarEmoji)
        assertEquals(BotAvatarPalette.DEFAULT, draft.avatarColor)
        assertEquals("Be careful.", draft.instructions)
        assertEquals(listOf("Review this", "x".repeat(StarterPrompts.MAX_LENGTH)), draft.starterPrompts)
    }

    @Test
    fun toBindings_includesEnabledToolsInStableOrder() {
        val bindings = BotDraft(
            currentDate = true,
            readUrl = true,
            webSearchConnectionUid = "search-1",
            mcpConnectionUids = setOf("mcp-b", "mcp-a")
        ).toBindings(botUid = "bot-1", now = 9)

        assertEquals(
            listOf(
                BuiltInAgentTool.CURRENT_DATE,
                BuiltInAgentTool.READ_URL,
                BotToolNames.WEB_SEARCH,
                BotToolNames.MCP_ALL,
                BotToolNames.MCP_ALL
            ),
            bindings.map { it.toolName }
        )
        assertEquals(listOf("mcp-a", "mcp-b"), bindings.mapNotNull { it.connectionUid }.takeLast(2))
        assertEquals(botBindingUid("bot-1", null, BuiltInAgentTool.CURRENT_DATE), bindings.first().bindingUid)
    }

    @Test
    fun fromBot_roundTripsToolToggles() {
        val bot = Bot(
            botUid = "bot-1",
            name = "Coder",
            avatarEmoji = "💻",
            avatarColor = "#006A6A",
            instructions = "Be careful.",
            defaultPlatformUid = "platform-1",
            defaultModel = "gpt-4.1",
            starterPrompts = StarterPrompts.encode(listOf("Review this")),
            builtin = true
        )
        val bindings = listOf(
            binding("bot-1", null, BuiltInAgentTool.READ_URL),
            binding("bot-1", "search-1", BotToolNames.WEB_SEARCH),
            binding("bot-1", "mcp-1", BotToolNames.MCP_ALL)
        )

        val draft = BotDraft.fromBot(bot, bindings)

        assertEquals("bot-1", draft.botUid)
        assertFalse(draft.currentDate)
        assertTrue(draft.readUrl)
        assertEquals("search-1", draft.webSearchConnectionUid)
        assertEquals(setOf("mcp-1"), draft.mcpConnectionUids)
        assertEquals(listOf("Review this"), draft.starterPrompts)
        assertEquals("platform-1" to "gpt-4.1", bot.modelOverride(setOf("platform-1")))
        assertNull(bot.modelOverride(setOf("other")))
    }

    @Test
    fun duplicatedBot_clearsBuiltinAndAppendsCopy() {
        val copy = duplicatedBot(
            source = Bot(botUid = "builtin-coder", name = "Coder", instructions = "Be careful.", builtin = true),
            newUid = "copy-1",
            now = 12
        )

        assertEquals("copy-1", copy.botUid)
        assertEquals("Coder copy", copy.name)
        assertFalse(copy.builtin)
        assertEquals("Be careful.", copy.instructions)
        assertEquals(12L, copy.createdAt)
    }

    @Test
    fun builtInBots_includeAssistantAndCoderWithReadTools() {
        val definitions = BuiltInBots.definitions()

        assertEquals(listOf(BuiltInBots.ASSISTANT_UID, BuiltInBots.CODER_UID), definitions.map { it.bot.botUid })
        definitions.forEach { definition ->
            assertTrue(definition.bot.builtin)
            assertEquals(
                listOf(BuiltInAgentTool.CURRENT_DATE, BuiltInAgentTool.READ_URL),
                definition.bindings.map { it.toolName }
            )
            assertTrue(definition.bot.starterPromptList().isNotEmpty())
        }
    }

    private fun binding(botUid: String, connectionUid: String?, toolName: String) = BotToolBinding(
        bindingUid = botBindingUid(botUid, connectionUid, toolName),
        botUid = botUid,
        connectionUid = connectionUid,
        toolName = toolName
    )
}
