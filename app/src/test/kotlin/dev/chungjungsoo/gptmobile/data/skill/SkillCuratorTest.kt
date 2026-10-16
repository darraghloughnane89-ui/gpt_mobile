package dev.chungjungsoo.gptmobile.data.skill

import org.junit.Assert.assertEquals
import org.junit.Test

class SkillCuratorTest {
    private val now = 1_800_000_000L

    @Test
    fun suggest_moreDownVotesThanUp_asksForARefine() {
        val suggestions = SkillCurator.suggest(
            listOf(skill(useCount = 3, up = 0, down = 2)),
            now
        )

        assertEquals(SkillProposalAction.REFINE, suggestions.single().action)
    }

    @Test
    fun suggest_unusedLearnedSkill_asksToRetireIt() {
        val suggestions = SkillCurator.suggest(
            listOf(
                skill(
                    origin = SkillOrigin.LEARNED,
                    createdAt = now - SkillCurator.UNUSED_LEARNED_AGE_SECONDS - 5
                )
            ),
            now
        )

        assertEquals(SkillProposalAction.RETIRE, suggestions.single().action)
    }

    @Test
    fun suggest_pinnedWeakSkill_asksToRefineAndDoesNotRetire() {
        val suggestions = SkillCurator.suggest(
            listOf(skill(useCount = 6, up = 0, down = 4, pinned = true)),
            now
        )

        assertEquals(SkillProposalAction.REFINE, suggestions.single().action)
    }

    private fun skill(
        useCount: Int = 0,
        up: Int = 0,
        down: Int = 0,
        pinned: Boolean = false,
        origin: String = SkillOrigin.USER,
        createdAt: Long = now
    ) = Skill(
        id = 3,
        stableName = "old-procedure",
        title = "Old",
        description = "Do the old thing",
        instructions = "Step 1",
        pinned = pinned,
        origin = origin,
        useCount = useCount,
        thumbsUp = up,
        thumbsDown = down,
        createdAt = createdAt,
        updatedAt = createdAt
    )
}
