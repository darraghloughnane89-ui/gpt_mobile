package dev.chungjungsoo.gptmobile.data.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillMarkdownTest {
    @Test
    fun parse_roundTrip_keepsProcedureToolsAndExamples() {
        val skill = Skill(
            id = 4,
            stableName = "deploy-staging",
            title = "Deploy staging",
            description = "Deploy the staging server",
            instructions = "1. Build\n2. Check /health",
            toolRequirements = SkillCodec.encodeList(listOf("shell", "web")),
            exampleInputs = SkillCodec.encodeList(listOf("deploy staging")),
            createdAt = 1,
            updatedAt = 1
        )

        val parsed = SkillMarkdown.parse(SkillMarkdown.export(skill))

        assertEquals("deploy-staging", parsed?.name)
        assertEquals("Deploy staging", parsed?.title)
        assertEquals("Deploy the staging server", parsed?.description)
        assertTrue(parsed?.instructions?.contains("Check /health") == true)
        assertEquals(listOf("shell", "web"), parsed?.tools)
        assertEquals(listOf("deploy staging"), parsed?.examples)
    }

    @Test
    fun parse_missingProcedure_returnsNull() {
        val markdown = """
            ---
            name: half
            description: Only a description
            ---
        """.trimIndent()

        assertNull(SkillMarkdown.parse(markdown))
    }
}
