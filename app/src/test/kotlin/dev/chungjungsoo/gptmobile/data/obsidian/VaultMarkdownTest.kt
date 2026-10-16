package dev.chungjungsoo.gptmobile.data.obsidian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultMarkdownTest {
    @Test
    fun render_parse_roundTrip_preservesChatMessagesLinksAndTags() {
        val original = VaultDocument(
            id = "chat-4",
            type = VaultNoteType.CHAT,
            updatedEpochSeconds = 1_763_000_000,
            tags = listOf("robin/chat", "inbox"),
            links = listOf("[[Robin/Projects/Inbox]]"),
            title = "Hello: world",
            project = "Inbox",
            messages = listOf(
                VaultMessage("m1", "user", "Hello", 1_763_000_000),
                VaultMessage("m2", "assistant", "Hi there", 1_763_000_010, platform = "openai")
            )
        )

        val parsed = VaultMarkdown.parse(VaultMarkdown.render(original))

        assertEquals(original, parsed)
        assertEquals(
            VaultMarkdown.render(original),
            VaultMarkdown.render(parsed!!)
        )
    }

    @Test
    fun render_parse_roundTrip_preservesSkillNameAndDescription() {
        val original = VaultDocument(
            id = "skill-summarise",
            type = VaultNoteType.SKILL,
            updatedEpochSeconds = 1_763_000_000,
            tags = listOf("robin/skill"),
            name = "summarise",
            description = "Summarise a note into bullets",
            title = "summarise",
            body = "Write three bullets."
        )

        val parsed = VaultMarkdown.parse(VaultMarkdown.render(original))

        assertEquals("summarise", parsed?.name)
        assertEquals("Summarise a note into bullets", parsed?.description)
        assertEquals("Write three bullets.", parsed?.body)
        assertEquals(VaultNoteType.SKILL, parsed?.type)
        assertEquals(original, parsed)
    }

    @Test
    fun render_parse_roundTrip_preservesMemoryBodyAndUnknownFrontmatter() {
        val original = VaultDocument(
            id = "mem-tea",
            type = VaultNoteType.MEMORY,
            updatedEpochSeconds = 1_763_000_000,
            tags = listOf("robin/memory"),
            links = listOf("[[Robin/Projects/Inbox]]"),
            title = "Prefers tea",
            project = "Inbox",
            body = "Darragh prefers tea.",
            extra = mapOf("source" to "hermes")
        )

        val parsed = VaultMarkdown.parse(VaultMarkdown.render(original))

        assertEquals(original, parsed)
    }

    @Test
    fun paths_customRoot_matchesFolderConvention() {
        assertEquals(
            "Notes/Robin/Chats/Inbox/2026-10-14 Hello.md",
            VaultPaths.chat("Notes/Robin", "Inbox", 1_792_000_000, "Hello", "chat-1")
        )
        assertEquals("Rocco/Memory/Prefers tea.md", VaultPaths.memory("Rocco", "Prefers tea", "mem-1"))
        assertEquals("Robin/Skills/summarise/SKILL.md", VaultPaths.skill("Robin", "summarise", "skill-1"))
        assertEquals("Robin/Bots/Ada.md", VaultPaths.bot("Robin", "Ada", "bot-1"))
        assertEquals("Robin/Projects/Inbox.md", VaultPaths.project("Robin", "Inbox", "project-1"))
        assertTrue(VaultPaths.isUnderRoot("Robin/Memory/Prefers tea.md", "Robin"))
        assertEquals("[[Robin/Projects/Inbox|Inbox]]", VaultPaths.projectWikiLink("Robin", "Inbox"))
    }
}
