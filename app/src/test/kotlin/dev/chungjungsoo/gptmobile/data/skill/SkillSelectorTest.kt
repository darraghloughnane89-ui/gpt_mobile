package dev.chungjungsoo.gptmobile.data.skill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillSelectorTest {
    private val deploy = skill(
        id = 1,
        name = "deploy-staging",
        description = "Deploy the staging server and verify the health check before announcing it",
        instructions = "1. Build the artifact.\n2. Deploy it.\n3. Check /health."
    )
    private val recipe = skill(
        id = 2,
        name = "sourdough",
        description = "Bake a sourdough loaf overnight",
        instructions = "Feed the starter. Mix. Proof. Bake."
    )

    @Test
    fun select_keywordOverlap_includesTheMatchingProcedure() {
        val selection = SkillSelector.select(
            SkillSelectionInput(
                skills = listOf(deploy, recipe),
                userText = "Please deploy staging now"
            )
        )

        assertFalse(selection.indexedOnly)
        assertEquals(listOf(1L), selection.injectedIds)
        assertTrue(selection.text.contains("### deploy-staging"))
        assertTrue(selection.text.contains("Check /health."))
        assertFalse(selection.text.contains("### sourdough"))
    }

    @Test
    fun select_noKeywordMatch_givesTheModelShortDescriptions() {
        val selection = SkillSelector.select(
            SkillSelectionInput(
                skills = listOf(deploy, recipe),
                userText = "What is the weather today"
            )
        )

        assertTrue(selection.indexedOnly)
        assertTrue(selection.injectedIds.isEmpty())
        assertTrue(selection.text.contains("deploy-staging:"))
        assertTrue(selection.text.contains("..."))
        assertTrue(SkillNames.routingDescription(deploy.description).length <= SkillNames.ROUTING_DESCRIPTION)
    }

    @Test
    fun select_manualName_loadsThatSkillEvenWhenTheBotDidNotLinkIt() {
        val selection = SkillSelector.select(
            SkillSelectionInput(
                skills = listOf(deploy, recipe),
                userText = "hello",
                manualNames = listOf("sourdough"),
                botLinkedIds = setOf(deploy.id)
            )
        )

        assertEquals(listOf(recipe.id), selection.injectedIds)
        assertTrue(selection.text.contains("Feed the starter"))
    }

    @Test
    fun select_charCap_dropsTheLowerRankedProcedure() {
        val selection = SkillSelector.select(
            SkillSelectionInput(
                skills = listOf(deploy, recipe),
                userText = "deploy staging and bake sourdough",
                charCap = 280
            )
        )

        assertTrue(selection.truncated || selection.injectedIds.size == 1)
        assertTrue(selection.text.length <= 280 + 4)
    }

    @Test
    fun select_disabledSkill_isLeftOut() {
        val selection = SkillSelector.select(
            SkillSelectionInput(
                skills = listOf(deploy.copy(enabled = false)),
                userText = "deploy staging",
                manualNames = listOf("deploy-staging")
            )
        )

        assertTrue(selection.text.isEmpty())
    }

    private fun skill(id: Long, name: String, description: String, instructions: String) = Skill(
        id = id,
        stableName = name,
        title = name,
        description = description,
        instructions = instructions,
        createdAt = 10,
        updatedAt = 10
    )
}
