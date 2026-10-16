package dev.chungjungsoo.gptmobile.data.instruction

import dev.chungjungsoo.gptmobile.data.context.CompactionKeys
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionStackTest {
    @Test
    fun compose_allBlank_returnsEmpty() {
        assertEquals("", InstructionStack().compose())
        assertEquals("", InstructionStack(global = "  ", project = "\n", bot = "", chat = null).compose())
    }

    @Test
    fun compose_onlyGlobal_keepsOriginalText() {
        val prompt = "  Be brief.  "
        assertEquals(prompt, InstructionStack(global = prompt).compose())
    }

    @Test
    fun compose_layers_followGlobalProjectBotChatOrder() {
        val composed = InstructionStack(
            global = "Platform prompt",
            project = "Use the project glossary.",
            bot = "You are a coder.",
            chat = "Answer in Spanish."
        ).compose()

        assertEquals(
            """
            Platform prompt

            Project instructions:
            Use the project glossary.

            Bot instructions:
            You are a coder.

            Chat instructions:
            Answer in Spanish.
            """.trimIndent(),
            composed
        )
    }

    @Test
    fun compose_nullProject_skipsProjectLayer() {
        val composed = InstructionStack(
            global = "Platform prompt",
            bot = "You are a coder."
        ).compose()

        assertEquals(
            """
            Platform prompt

            Bot instructions:
            You are a coder.
            """.trimIndent(),
            composed
        )
        assertTrue(!composed.contains("Project instructions"))
    }

    @Test
    fun composer_nullProjectSource_leavesProjectEmpty() = runBlocking {
        val stack = InstructionStackComposer(NullProjectInstructionSource).forChat(
            chatId = 4,
            global = "Platform prompt",
            bot = "Persona",
            chat = null
        )

        assertNull(stack.project)
        assertEquals("Persona", stack.bot)
    }

    @Test
    fun withInstructionStack_platformPromptOnly_keepsSamePlatform() {
        val platform = samplePlatform(systemPrompt = "Be brief.")
        val stacked = platform.withInstructionStack(InstructionStack(global = platform.systemPrompt))

        assertSame(platform, stacked)
        assertEquals(
            CompactionKeys.sourceFingerprint(emptyList(), platform),
            CompactionKeys.sourceFingerprint(emptyList(), stacked)
        )
    }

    @Test
    fun withInstructionStack_botInstructions_changeCompactionFingerprint() {
        val platform = samplePlatform(systemPrompt = "Be brief.")
        val stacked = platform.withInstructionStack(
            InstructionStack(global = platform.systemPrompt, bot = "You are a coder.")
        )

        assertTrue(stacked.systemPrompt!!.contains("You are a coder."))
        assertNotEquals(
            CompactionKeys.sourceFingerprint(emptyList(), platform),
            CompactionKeys.sourceFingerprint(emptyList(), stacked)
        )
    }

    private fun samplePlatform(systemPrompt: String) = PlatformV2(
        name = "OpenAI",
        compatibleType = ClientType.OPENAI,
        apiUrl = "https://api.openai.com/v1/",
        model = "gpt-4.1",
        systemPrompt = systemPrompt
    )
}
