package dev.chungjungsoo.gptmobile.data.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillSlashParserTest {
    private val known = setOf("deploy-staging", "recipe", "ocr", "alpha", "beta", "gamma", "delta", "epsilon")

    @Test
    fun parse_plainText_keepsTheMessage() {
        val parsed = SkillSlashParser.parse("ship the build", known)

        assertTrue(parsed.skillNames.isEmpty())
        assertFalse(parsed.saveAsSkill)
        assertEquals("ship the build", parsed.remainder)
    }

    @Test
    fun parse_leadingSkill_stripsTheNameAndKeepsTheRequest() {
        val parsed = SkillSlashParser.parse("/Deploy-Staging ship it", known)

        assertEquals(listOf("deploy-staging"), parsed.skillNames)
        assertEquals("ship it", parsed.remainder)
    }

    @Test
    fun parse_unknownToken_stopsAndLeavesTheRest() {
        val parsed = SkillSlashParser.parse("/deploy-staging /missing ship", known)

        assertEquals(listOf("deploy-staging"), parsed.skillNames)
        assertEquals("/missing ship", parsed.remainder)
    }

    @Test
    fun parse_learn_marksSaveAndKeepsRemainder() {
        val parsed = SkillSlashParser.parse("/learn how I deployed staging", known)

        assertTrue(parsed.saveAsSkill)
        assertEquals("how I deployed staging", parsed.remainder)
        assertTrue(SkillSlashParser.isBareLearn("/learn"))
        assertFalse(SkillSlashParser.isBareLearn("/learn how"))
    }

    @Test
    fun parse_moreThanFiveSkills_keepsTheFirstFive() {
        val parsed = SkillSlashParser.parse("/alpha /beta /gamma /delta /epsilon /recipe go", known)

        assertEquals(listOf("alpha", "beta", "gamma", "delta", "epsilon"), parsed.skillNames)
        assertEquals("/recipe go", parsed.remainder)
    }
}
