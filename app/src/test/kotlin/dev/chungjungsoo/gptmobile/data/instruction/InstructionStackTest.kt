package dev.chungjungsoo.gptmobile.data.instruction

import dev.chungjungsoo.gptmobile.data.skill.MemorySkillStore
import dev.chungjungsoo.gptmobile.data.skill.Skill
import dev.chungjungsoo.gptmobile.data.skill.SkillInstructionProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionStackTest {
    @Test
    fun compose_appendsTheSkillSliceAfterThePlatformPrompt() = runTest {
        val store = MemorySkillStore()
        store.insertSkill(
            Skill(
                stableName = "deploy-staging",
                title = "Deploy",
                description = "Deploy the staging server",
                instructions = "Check /health.",
                createdAt = 1,
                updatedAt = 1
            )
        )
        val stack = InstructionStack(listOf(SkillInstructionProvider(store)))

        val composition = stack.compose(
            InstructionContext(
                basePrompt = "You are a careful assistant.",
                userText = "deploy staging",
                manualSkillNames = listOf("deploy-staging")
            )
        )

        assertTrue(composition.text.startsWith("You are a careful assistant."))
        assertTrue(composition.text.contains("Check /health."))
        assertEquals(InstructionOrder.SKILLS, composition.segments.single().key.let { SkillInstructionProvider(store).order })
        assertEquals("skills", composition.segments.single().key)
        assertTrue(InstructionOrder.SKILLS > InstructionOrder.BOT)
    }

    @Test
    fun merge_emptyExtra_keepsTheBasePrompt() {
        assertEquals("Base", InstructionStack.merge("Base", "  "))
    }
}
