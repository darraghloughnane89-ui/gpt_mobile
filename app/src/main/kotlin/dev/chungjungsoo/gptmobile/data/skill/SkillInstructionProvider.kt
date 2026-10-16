package dev.chungjungsoo.gptmobile.data.skill

import dev.chungjungsoo.gptmobile.data.instruction.InstructionContext
import dev.chungjungsoo.gptmobile.data.instruction.InstructionOrder
import dev.chungjungsoo.gptmobile.data.instruction.InstructionProvider
import dev.chungjungsoo.gptmobile.data.instruction.InstructionSegment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillInstructionProvider @Inject constructor(
    private val store: SkillStore
) : InstructionProvider {
    override val key: String = KEY
    override val order: Int = InstructionOrder.SKILLS

    override suspend fun provide(context: InstructionContext): InstructionSegment? {
        val skills = store.listSkills()
        val linked = context.botUid?.let { botUid -> store.listLinkIds(botUid).toSet() }
        val selection = SkillSelector.select(
            SkillSelectionInput(
                skills = skills,
                userText = context.userText,
                manualNames = context.manualSkillNames,
                botLinkedIds = linked
            )
        )
        if (selection.text.isBlank()) return null
        return InstructionSegment(
            key = key,
            text = selection.text,
            skillIds = selection.injectedIds
        )
    }

    companion object {
        const val KEY = "skills"
    }
}
