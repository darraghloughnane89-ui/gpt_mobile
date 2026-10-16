package dev.chungjungsoo.gptmobile.data.skill

data class CuratorSuggestion(
    val skillId: Long,
    val action: String,
    val rationale: String,
    val draft: SkillDraft
)

/**
 * The garbage-collector half of the Hermes loop: usage and thumbs decide
 * whether to suggest a rewrite or retirement. Pinned skills are kept.
 * Nothing is deleted here. Suggestions become inbox proposals.
 */
object SkillCurator {
    const val REFINE_MIN_USES = 3
    const val REFINE_MIN_DOWN = 2
    const val RETIRE_MIN_USES = 4
    const val RETIRE_MIN_DOWN = 3
    const val UNUSED_LEARNED_AGE_SECONDS = 30L * 24 * 60 * 60

    fun suggest(skills: List<Skill>, nowSeconds: Long): List<CuratorSuggestion> = skills.mapNotNull { skill ->
        if (!skill.enabled) return@mapNotNull null
        val weak = !skill.pinned &&
            skill.useCount >= RETIRE_MIN_USES &&
            skill.thumbsDown >= RETIRE_MIN_DOWN &&
            skill.thumbsUp == 0
        val unusedLearned = !skill.pinned &&
            skill.origin == SkillOrigin.LEARNED &&
            skill.useCount == 0 &&
            nowSeconds - skill.createdAt >= UNUSED_LEARNED_AGE_SECONDS
        val refine = skill.useCount >= REFINE_MIN_USES &&
            skill.thumbsDown >= REFINE_MIN_DOWN &&
            skill.thumbsDown > skill.thumbsUp
        when {
            weak -> retire(skill, "Thumbs-down with no thumbs-up after ${skill.useCount} uses. Retire it or rewrite the procedure.")
            unusedLearned -> retire(skill, "Learned skill was never used. Retire it unless you still want it.")
            refine -> CuratorSuggestion(
                skillId = skill.id,
                action = SkillProposalAction.REFINE,
                rationale = "More thumbs-down than thumbs-up after ${skill.useCount} uses. Tighten the procedure before the next chat.",
                draft = skill.toDraft()
            )
            else -> null
        }
    }

    private fun retire(skill: Skill, rationale: String): CuratorSuggestion = CuratorSuggestion(
        skillId = skill.id,
        action = SkillProposalAction.RETIRE,
        rationale = rationale,
        draft = skill.toDraft()
    )
}
