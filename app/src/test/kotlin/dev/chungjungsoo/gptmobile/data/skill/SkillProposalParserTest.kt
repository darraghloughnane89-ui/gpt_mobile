package dev.chungjungsoo.gptmobile.data.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillProposalParserTest {
    @Test
    fun parse_fencedJson_readsSkillAndMemory() {
        val raw = """
            Here is the review:
            ```json
            {"skills":[{"action":"create","name":"Deploy Staging","description":"Deploy staging","instructions":"Check health.","tools":["shell"],"examples":["ship staging"],"rationale":"It took several steps."}],"memories":[{"text":"Staging host is staging.example"}]}
            ```
        """.trimIndent()

        val parsed = SkillProposalParser.parse(raw)

        assertEquals(1, parsed?.skills?.size)
        assertEquals("deploy-staging", parsed?.skills?.first()?.name)
        assertEquals(SkillProposalAction.CREATE, parsed?.skills?.first()?.action)
        assertEquals(listOf("Staging host is staging.example"), parsed?.memories)
    }

    @Test
    fun parse_updateWithoutInstructions_dropsTheItem() {
        val parsed = SkillProposalParser.parse("""{"skills":[{"action":"update","name":"recipe","description":"Bake","instructions":""}],"memories":[]}""")

        assertTrue(parsed?.skills?.isEmpty() == true)
    }

    @Test
    fun parse_proseWithoutJson_returnsNull() {
        assertEquals(null, SkillProposalParser.parse("Nothing useful happened."))
    }
}
