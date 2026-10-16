package dev.chungjungsoo.gptmobile.data.skill

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillApplier @Inject constructor(
    private val store: SkillStore,
    private val memoryWriter: MemoryWriter,
    private val clock: SkillClock
) {
    suspend fun apply(proposal: SkillProposal, override: SkillDraft? = null): ApplyResult {
        if (proposal.status != SkillProposalStatus.PENDING) {
            return ApplyResult.Blocked("This proposal is already resolved.")
        }
        val now = clock.nowSeconds()
        val result = when (proposal.action) {
            SkillProposalAction.MEMORY -> ApplyResult.Saved(null)
            SkillProposalAction.RETIRE -> retire(proposal)
            SkillProposalAction.CREATE,
            SkillProposalAction.UPDATE,
            SkillProposalAction.REFINE -> upsert(proposal, override, now)
            else -> ApplyResult.Blocked("Unknown proposal.")
        }
        if (result is ApplyResult.Blocked) return result
        writeMemories(proposal)
        store.updateProposal(proposal.copy(status = SkillProposalStatus.ACCEPTED, resolvedAt = now))
        return result
    }

    suspend fun reject(proposal: SkillProposal) {
        if (proposal.status != SkillProposalStatus.PENDING) return
        store.updateProposal(
            proposal.copy(status = SkillProposalStatus.REJECTED, resolvedAt = clock.nowSeconds())
        )
    }

    private suspend fun retire(proposal: SkillProposal): ApplyResult {
        val skill = proposal.targetSkillId?.let { store.getSkill(it) }
            ?: proposal.proposedName.takeIf { it.isNotBlank() }?.let { store.getByName(it) }
        if (skill == null) return ApplyResult.Saved(null)
        if (skill.pinned) return ApplyResult.Blocked("Unpin the skill before retiring it.")
        store.deleteSkill(skill.id)
        return ApplyResult.Saved(skill.id)
    }

    private suspend fun upsert(proposal: SkillProposal, override: SkillDraft?, now: Long): ApplyResult {
        val draft = override ?: proposal.toDraft()
        val name = SkillNames.slug(draft.name) ?: return ApplyResult.Blocked("Use a short lowercase name with hyphens.")
        val description = draft.description.trim()
        val instructions = draft.instructions.trim().ifBlank { proposal.proposedInstructions.trim() }
        if (description.isEmpty()) return ApplyResult.Blocked("Add a when-to-use description.")
        if (instructions.isEmpty()) return ApplyResult.Blocked("Add the procedure.")
        val existing = proposal.targetSkillId?.let { store.getSkill(it) } ?: store.getByName(name)
        val title = draft.title.trim().ifBlank { name }
        if (existing == null) {
            val id = store.insertSkill(
                Skill(
                    stableName = name,
                    title = title,
                    description = description.take(SkillNames.MAX_DESCRIPTION),
                    instructions = instructions.take(SkillNames.MAX_INSTRUCTIONS),
                    toolRequirements = SkillCodec.encodeList(draft.tools),
                    exampleInputs = SkillCodec.encodeList(draft.examples),
                    pinned = draft.pinned,
                    origin = SkillOrigin.LEARNED,
                    createdAt = now,
                    updatedAt = now
                )
            )
            return ApplyResult.Saved(id)
        }
        val saved = existing.copy(
            stableName = name,
            title = title,
            description = description.take(SkillNames.MAX_DESCRIPTION),
            instructions = instructions.take(SkillNames.MAX_INSTRUCTIONS),
            toolRequirements = SkillCodec.encodeList(draft.tools),
            exampleInputs = SkillCodec.encodeList(draft.examples),
            updatedAt = now
        )
        store.updateSkill(saved)
        return ApplyResult.Saved(existing.id)
    }

    private suspend fun writeMemories(proposal: SkillProposal) {
        val facts = SkillCodec.memories(proposal).map { text ->
            MemoryFact(text = text, source = "skill-review", chatId = proposal.chatId)
        }
        if (facts.isNotEmpty()) memoryWriter.write(facts)
    }
}
