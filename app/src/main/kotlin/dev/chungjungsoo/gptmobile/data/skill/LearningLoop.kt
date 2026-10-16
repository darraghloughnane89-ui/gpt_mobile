package dev.chungjungsoo.gptmobile.data.skill

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Hermes-style loop for a phone:
 * a counter decides when to reflect, the model critic proposes a skill or memory,
 * and the user approves it unless auto-accept is on. Retirement of a pinned skill stays in the inbox.
 */
@Singleton
class LearningLoop @Inject constructor(
    private val store: SkillStore,
    private val reviewer: SkillReviewer,
    private val applier: SkillApplier,
    private val clock: SkillClock
) {
    fun shouldReview(request: LearningRequest): Boolean {
        val userTurns = request.turns.filter { it.role == "user" && it.text.isNotBlank() }
        val assistantTurns = request.turns.filter { it.role == "assistant" && it.text.isNotBlank() }
        if (userTurns.isEmpty() || assistantTurns.isEmpty()) return false
        if (request.explicit) return true
        return userTurns.size >= 2 && userTurns.sumOf { it.text.length } >= 80
    }

    suspend fun review(request: LearningRequest): LearningResult {
        if (!shouldReview(request)) return LearningResult.Skipped(request.explicit)
        val skills = store.listSkills()
        val raw = try {
            reviewer.review(SkillReviewPrompt.SYSTEM, SkillReviewPrompt.user(SkillTranscript.bounded(request.turns), skills))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return if (request.explicit) {
                LearningResult.Failed(error.message ?: "Skill review failed.")
            } else {
                LearningResult.Skipped(false)
            }
        }
        val parsed = SkillProposalParser.parse(raw)
        if (parsed == null) {
            return if (request.explicit) {
                LearningResult.Failed("The review did not include a skill proposal.")
            } else {
                LearningResult.Skipped(false)
            }
        }
        if (parsed.skills.isEmpty() && parsed.memories.isEmpty()) {
            return LearningResult.Skipped(request.explicit)
        }
        return file(parsed, request)
    }

    suspend fun fileCuratorSuggestions() {
        val now = clock.nowSeconds()
        val pending = store.listProposals(SkillProposalStatus.PENDING)
        SkillCurator.suggest(store.listSkills(), now).forEach { suggestion ->
            val duplicate = pending.any { proposal ->
                proposal.targetSkillId == suggestion.skillId && proposal.action == suggestion.action
            }
            if (duplicate) return@forEach
            store.insertProposal(
                SkillProposal(
                    action = suggestion.action,
                    targetSkillId = suggestion.skillId,
                    proposedName = suggestion.draft.name,
                    proposedDescription = suggestion.draft.description,
                    proposedInstructions = suggestion.draft.instructions,
                    proposedTools = SkillCodec.encodeList(suggestion.draft.tools),
                    proposedExamples = SkillCodec.encodeList(suggestion.draft.examples),
                    rationale = suggestion.rationale,
                    source = SkillProposalSource.CURATOR,
                    createdAt = now
                )
            )
        }
    }

    private suspend fun file(parsed: ParsedReview, request: LearningRequest): LearningResult {
        val now = clock.nowSeconds()
        val autoAccept = store.getSettings().autoAccept
        val source = if (request.explicit) SkillProposalSource.SAVE_AS_SKILL else SkillProposalSource.CHAT_END
        val skillsByName = store.listSkills().associateBy { it.stableName }
        val proposals = mutableListOf<SkillProposal>()
        parsed.skills.forEach { item ->
            val existing = skillsByName[item.name]
            val action = when {
                item.action == SkillProposalAction.RETIRE -> SkillProposalAction.RETIRE
                existing != null -> SkillProposalAction.UPDATE
                else -> SkillProposalAction.CREATE
            }
            if (action == SkillProposalAction.RETIRE && existing == null) return@forEach
            proposals += SkillProposal(
                chatId = request.chatId.takeIf { it > 0 },
                action = action,
                targetSkillId = existing?.id,
                proposedName = item.name,
                proposedDescription = item.description,
                proposedInstructions = item.instructions,
                proposedTools = SkillCodec.encodeList(item.tools),
                proposedExamples = SkillCodec.encodeList(item.examples),
                rationale = item.rationale,
                memoryFactsJson = "[]",
                source = source,
                createdAt = now
            )
        }
        if (parsed.memories.isNotEmpty()) {
            val memoryJson = SkillCodec.encodeList(parsed.memories)
            if (proposals.isEmpty()) {
                proposals += SkillProposal(
                    chatId = request.chatId.takeIf { it > 0 },
                    action = SkillProposalAction.MEMORY,
                    proposedName = "",
                    proposedDescription = "",
                    proposedInstructions = "",
                    rationale = "Facts from this chat.",
                    memoryFactsJson = memoryJson,
                    source = source,
                    createdAt = now
                )
            } else {
                proposals[0] = proposals[0].copy(memoryFactsJson = memoryJson)
            }
        }
        if (proposals.isEmpty()) return LearningResult.Skipped(request.explicit)
        var autoAccepted = 0
        proposals.forEach { proposal ->
            val id = store.insertProposal(proposal)
            val stored = proposal.copy(id = id)
            if (autoAccept && canAutoAccept(stored, skillsByName)) {
                val applied = applier.apply(stored)
                if (applied is ApplyResult.Saved) autoAccepted += 1
            }
        }
        return LearningResult.Proposed(proposals.size, autoAccepted)
    }

    private fun canAutoAccept(proposal: SkillProposal, skillsByName: Map<String, Skill>): Boolean {
        if (proposal.action != SkillProposalAction.RETIRE) return true
        val skill = proposal.targetSkillId?.let { id -> skillsByName.values.firstOrNull { it.id == id } }
            ?: skillsByName[proposal.proposedName]
        return skill?.pinned != true
    }
}
