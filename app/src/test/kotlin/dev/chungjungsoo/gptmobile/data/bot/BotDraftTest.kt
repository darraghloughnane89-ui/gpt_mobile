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
    fun toBindings_includesJavascriptWhenEnabled() {
        val bindings = BotDraft(runJavascript = true).toBindings(botUid = "bot-1", now = 9)

        assertEquals(listOf(BuiltInAgentTool.RUN_JAVASCRIPT), bindings.map { it.toolName })
        val draft = BotDraft.fromBot(
            Bot(botUid = "bot-1", name = "Coder", instructions = "Be careful."),
            bindings
        )
        assertTrue(draft.runJavascript)
        assertFalse(draft.readUrl)
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

        assertTrue(definitions.map { it.bot.botUid }.contains(BuiltInBots.ASSISTANT_UID))
        assertTrue(definitions.map { it.bot.botUid }.contains(BuiltInBots.CODER_UID))
        definitions.forEach { definition ->
            assertTrue(definition.bot.builtin)
            assertEquals(
                listOf(BuiltInAgentTool.CURRENT_DATE, BuiltInAgentTool.READ_URL),
                definition.bindings.map { it.toolName }
            )
            assertTrue(definition.bot.starterPromptList().isNotEmpty())
            assertTrue(definition.bot.instructions.length <= BotDraft.MAX_INSTRUCTIONS_LENGTH)
        }
    }

    @Test
    fun starterBots_matchTheSeededPrompts_andCanBeRemovedFromTheMissingSet() {
        val byName = BuiltInBots.definitions().associateBy { it.bot.name }
        assertEquals(
            "You are the CBRE Field Agent for Darragh Loughnane, electrician with 20 years experience, " +
                "performing PPM and reactive maintenance across Bank of Ireland sites in Wexford and surrounding areas. " +
                "Supervisor: Gary Lynch.\n\n" +
                "Core rules (never break): Dates in DD-MM-YYYY. Asset tags exactly as ASSET/BOI/ROI/#####. " +
                "Work orders: P=planned/PPM, C=reactive or PPM code, R=remedial. " +
                "Capture isolation/spur label per asset (e.g. SDB2 RCBO 1T). " +
                "If none visible, record \"none visible — to be confirmed\". Never leave blank. No invented data. " +
                "If uncertain from photos/voice notes: \"appears / to be confirmed\". " +
                "Escalate real safety issues (leaks, corrosion, water on electrics, legionella risk) immediately. " +
                "One record per PPM/WO. Honour scope. Photos are additive. Exact strings copied, never retyped. " +
                "SI is master system — you cannot access it. Uncertain = \"the answer is in SI\". " +
                "Never submit to Smartsheet. Draft only. " +
                "Keep classes distinct (boiling-water dispenser ≠ stored water heater; solar thermal ≠ PV). " +
                "Input is photos + short voice-to-text. Read fragments charitably. No AI trace in output.\n\n" +
                "Output: structured, professional but practical site visit report ready for upload. " +
                "Include asset ID, location, findings, actions, isolation status, photos referenced, next steps. " +
                "Flag any compliance or safety items clearly at top.",
            byName.getValue("CBRE Field Reports").bot.instructions
        )
        assertTrue(byName.getValue("Gridwell Quoting").bot.instructions.contains("SEAI grants"))
        assertTrue(byName.getValue("Gridwell Quoting").bot.instructions.contains("to be confirmed from survey"))
        assertTrue(byName.getValue("Coding Helper").bot.instructions.contains("Android/Kotlin"))
        assertTrue(byName.getValue("Loughnane Lifts Coach").bot.instructions.contains("progressive overload"))
        assertTrue(byName.getValue("Email Drafter").bot.instructions.contains("ready-to-send drafts"))
        assertTrue(byName.getValue("Research Bot").bot.instructions.contains("Summarise key points first"))
        assertTrue(byName.getValue("Daily Planner").bot.instructions.contains("Wexford sites"))
        assertTrue(byName.getValue("Voice Companion").bot.instructions.contains("Avoid long monologues"))

        val starterUids = listOf(
            BuiltInBots.CBRE_FIELD_REPORTS_UID,
            BuiltInBots.GRIDWELL_QUOTING_UID,
            BuiltInBots.CODING_HELPER_UID,
            BuiltInBots.LOUGHNANE_LIFTS_UID,
            BuiltInBots.EMAIL_DRAFTER_UID,
            BuiltInBots.RESEARCH_UID,
            BuiltInBots.DAILY_PLANNER_UID,
            BuiltInBots.VOICE_COMPANION_UID
        )
        assertEquals(starterUids, BuiltInBots.missing(setOf(BuiltInBots.ASSISTANT_UID, BuiltInBots.CODER_UID)).map { it.bot.botUid })
        assertEquals(BuiltInBots.cbreFieldReports().bot, BuiltInBots.definition(BuiltInBots.CBRE_FIELD_REPORTS_UID)?.bot)
        assertNull(BuiltInBots.definition("custom-bot"))
        assertTrue(BuiltInBots.missing(BuiltInBots.definitions().map { it.bot.botUid }.toSet()).isEmpty())
    }

    private fun binding(botUid: String, connectionUid: String?, toolName: String) = BotToolBinding(
        bindingUid = botBindingUid(botUid, connectionUid, toolName),
        botUid = botUid,
        connectionUid = connectionUid,
        toolName = toolName
    )
}
