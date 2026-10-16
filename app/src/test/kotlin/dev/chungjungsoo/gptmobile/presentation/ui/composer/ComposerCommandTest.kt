package dev.chungjungsoo.gptmobile.presentation.ui.composer

import dev.chungjungsoo.gptmobile.data.prompt.SavedPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerCommandTest {
    private val features = composerFeaturesAvailableOnMain()
    private val summarize = SavedPrompt(id = "p1", title = "Summarize", body = "Summarize {topic} for {audience}", favourite = true)
    private val note = SavedPrompt(id = "p2", title = "Note", body = "Remember this.", favourite = false)

    @Test
    fun mainFeatures_hideSkillAndClear() {
        assertTrue(ComposerFeature.SKILL !in features)
        assertTrue(ComposerFeature.CLEAR !in features)
        val tokens = slashMenuRows("", features, emptyList())
            .filterIsInstance<SlashMenuRow.Command>()
            .map { it.command.token }
        assertEquals(listOf("bot", "model", "project", "search", "image", "temp", "export"), tokens)
    }

    @Test
    fun slashQuery_filtersCommandsAndPrompts() {
        assertNull(detectSlashQuery("hello", 5))
        assertNull(detectSlashQuery("/bot", 0))
        assertEquals("bo", detectSlashQuery("/bo", 3)?.filter)
        assertNull(detectSlashQuery("/bot\nnext", 6))

        val rows = slashMenuRows("mo", features, listOf(summarize, note))
        assertEquals(listOf(SlashCommand.MODEL), rows.filterIsInstance<SlashMenuRow.Command>().map { it.command })
        assertTrue(rows.filterIsInstance<SlashMenuRow.Prompt>().isEmpty())
        assertTrue(rows.last() is SlashMenuRow.Library)

        val prompts = slashMenuRows("sum", features, listOf(note, summarize))
            .filterIsInstance<SlashMenuRow.Prompt>()
            .map { it.prompt.title }
        assertEquals(listOf("Summarize"), prompts)
    }

    @Test
    fun mentionQuery_isTheActiveAtToken() {
        assertNull(detectMentionQuery("user@host", 9))
        assertNull(detectMentionQuery("/bot @ada", 9))
        val query = detectMentionQuery("ask @ad", 7)
        assertEquals("ad", query?.filter)
        assertEquals(4, query?.start)
        assertEquals(7, query?.end)

        val rows = mentionMenuRows(
            filter = "des",
            bots = listOf(MentionCandidate("b1", "Ada")),
            projects = listOf(MentionCandidate("3", "Design"), MentionCandidate("4", "Home"))
        )
        assertEquals(listOf("Design"), rows.filterIsInstance<MentionMenuRow.Project>().map { it.candidate.label })
        assertTrue(rows.filterIsInstance<MentionMenuRow.Bot>().isEmpty())
    }

    @Test
    fun commands_wireToExistingActions() {
        assertEquals(ComposerAction.OpenBot, actionForCommand(SlashCommand.BOT, "bo"))
        assertEquals(ComposerAction.SearchConversation("cats"), actionForCommand(SlashCommand.SEARCH, "search cats"))
        assertEquals(ComposerAction.OpenSearch, actionForCommand(SlashCommand.SEARCH, "search"))
        assertEquals(ComposerAction.SetTemperature(0.7f), actionForCommand(SlashCommand.TEMP, "temp 0.7"))
        assertEquals(ComposerAction.OpenTemperature, actionForCommand(SlashCommand.TEMP, "temp 4"))
        assertEquals(ComposerAction.None, actionForCommand(SlashCommand.SKILL, "skill"))
        assertEquals(ComposerAction.None, actionForCommand(SlashCommand.CLEAR, "clear"))
        assertNull(parseTemperature("nope"))
        assertEquals(0f, parseTemperature("0")!!)
        assertNull(parseTemperature("2.1"))
    }

    @Test
    fun submit_runsCommandsAndOpensPlaceholderForms() {
        assertEquals(ComposerSubmit.Message, interpretComposerSubmit("hello", features, listOf(summarize)))
        assertEquals(ComposerSubmit.Message, interpretComposerSubmit("/skill", features, emptyList()))
        assertEquals(
            ComposerSubmit.Run(ComposerAction.ExportChat),
            interpretComposerSubmit("/export", features, emptyList())
        )
        assertEquals(
            ComposerSubmit.Run(ComposerAction.PickImage),
            interpretComposerSubmit("/image", features, emptyList())
        )
        val promptSubmit = interpretComposerSubmit("/Summarize", features, listOf(summarize, note))
        val fill = (promptSubmit as ComposerSubmit.Run).action as ComposerAction.FillPrompt
        assertEquals("Summarize", fill.prompt.title)
        val noteSubmit = interpretComposerSubmit("/note", features, listOf(summarize, note))
        val insert = (noteSubmit as ComposerSubmit.Run).action as ComposerAction.InsertText
        assertEquals("Remember this.", insert.text)
        assertEquals(
            "Summarize gravity for me",
            filledPrompt(summarize.body, mapOf("topic" to "gravity", "audience" to "me"))
        )
    }

    @Test
    fun conversationSearch_matchesEitherSideOfATurn() {
        val matches = conversationMatchIndices(
            userContents = listOf("Hello", "Where is the cat?"),
            assistantContents = listOf(listOf("A dog"), listOf("No pets")),
            query = "dog"
        )
        assertEquals(listOf(0), matches)
        assertEquals(listOf(0, 1), conversationMatchIndices(listOf("a", "b"), emptyList(), " "))
    }
}
