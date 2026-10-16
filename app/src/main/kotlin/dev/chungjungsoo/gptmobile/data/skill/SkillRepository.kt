package dev.chungjungsoo.gptmobile.data.skill

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillRepository @Inject constructor(
    private val store: SkillStore,
    private val applier: SkillApplier,
    private val clock: SkillClock
) : BotSkillLinkStore {
    suspend fun listSkills(): List<Skill> = store.listSkills()

    suspend fun get(id: Long): Skill? = store.getSkill(id)

    suspend fun summaries(): List<SkillSummary> = store.listSkills()
        .filter { it.enabled }
        .map { skill -> SkillSummary(skill.id, skill.stableName, SkillNames.routingDescription(skill.description)) }

    suspend fun save(id: Long?, draft: SkillDraft): SkillWriteResult {
        val name = SkillNames.slug(draft.name) ?: return SkillWriteResult.Invalid("Use a short lowercase name with hyphens.")
        val description = draft.description.trim()
        val instructions = draft.instructions.trim()
        if (description.isEmpty()) return SkillWriteResult.Invalid("Add a when-to-use description.")
        if (instructions.isEmpty()) return SkillWriteResult.Invalid("Add the procedure.")
        val now = clock.nowSeconds()
        val conflict = store.getByName(name)
        if (conflict != null && conflict.id != id) {
            return SkillWriteResult.Invalid("A skill named $name already exists.")
        }
        val title = draft.title.trim().ifBlank { name }
        val existing = id?.let { store.getSkill(it) }
        val skill = if (existing == null) {
            val newId = store.insertSkill(
                Skill(
                    stableName = name,
                    title = title,
                    description = description.take(SkillNames.MAX_DESCRIPTION),
                    instructions = instructions.take(SkillNames.MAX_INSTRUCTIONS),
                    toolRequirements = SkillCodec.encodeList(draft.tools),
                    exampleInputs = SkillCodec.encodeList(draft.examples),
                    pinned = draft.pinned,
                    origin = draft.origin.ifBlank { SkillOrigin.USER },
                    createdAt = now,
                    updatedAt = now
                )
            )
            store.getSkill(newId)!!
        } else {
            val updated = existing.copy(
                stableName = name,
                title = title,
                description = description.take(SkillNames.MAX_DESCRIPTION),
                instructions = instructions.take(SkillNames.MAX_INSTRUCTIONS),
                toolRequirements = SkillCodec.encodeList(draft.tools),
                exampleInputs = SkillCodec.encodeList(draft.examples),
                pinned = draft.pinned,
                updatedAt = now
            )
            store.updateSkill(updated)
            updated
        }
        return SkillWriteResult.Ok(skill)
    }

    suspend fun duplicate(id: Long): SkillWriteResult {
        val source = store.getSkill(id) ?: return SkillWriteResult.Invalid("That skill is gone.")
        val name = uniqueCopyName(source.stableName)
        return save(
            id = null,
            draft = source.toDraft().copy(
                name = name,
                title = "Copy of ${source.title}",
                pinned = false,
                origin = SkillOrigin.USER
            )
        )
    }

    suspend fun delete(id: Long): SkillWriteResult {
        val skill = store.getSkill(id) ?: return SkillWriteResult.Invalid("That skill is gone.")
        if (skill.pinned) return SkillWriteResult.Invalid("Unpin the skill before deleting it.")
        store.deleteSkill(id)
        return SkillWriteResult.Ok(skill)
    }

    suspend fun importMarkdown(markdown: String): SkillWriteResult {
        val draft = SkillMarkdown.parse(markdown) ?: return SkillWriteResult.Invalid("That file needs a name, a description, and a procedure.")
        return save(null, draft)
    }

    suspend fun exportMarkdown(id: Long): String? = store.getSkill(id)?.let(SkillMarkdown::export)

    suspend fun recordUse(ids: List<Long>) {
        val now = clock.nowSeconds()
        ids.distinct().forEach { id -> store.recordUse(id, now) }
    }

    suspend fun recordFeedback(ids: List<Long>, up: Boolean) {
        val now = clock.nowSeconds()
        ids.distinct().forEach { id -> store.addThumb(id, up, now) }
    }

    suspend fun autoAccept(): Boolean = store.getSettings().autoAccept

    suspend fun setAutoAccept(enabled: Boolean) {
        store.saveSettings(SkillSettings(autoAccept = enabled))
    }

    suspend fun pendingProposals(): List<SkillProposal> = store.listProposals(SkillProposalStatus.PENDING)

    suspend fun accept(id: Long, draft: SkillDraft?): ApplyResult {
        val proposal = store.getProposal(id) ?: return ApplyResult.Blocked("That proposal is gone.")
        return applier.apply(proposal, draft)
    }

    suspend fun reject(id: Long) {
        val proposal = store.getProposal(id) ?: return
        applier.reject(proposal)
    }

    override suspend fun skillsForBot(botUid: String): List<Skill> {
        val ids = store.listLinkIds(botUid).toSet()
        return store.listSkills().filter { it.id in ids }
    }

    override suspend fun linkSkill(botUid: String, skillId: Long) {
        val current = store.listLinkIds(botUid)
        if (skillId in current) return
        if (store.getSkill(skillId) == null) return
        store.replaceLinks(botUid, current + skillId, clock.nowSeconds())
    }

    override suspend fun unlinkSkill(botUid: String, skillId: Long) {
        val current = store.listLinkIds(botUid).filter { it != skillId }
        store.replaceLinks(botUid, current, clock.nowSeconds())
    }

    override suspend fun replaceBotSkills(botUid: String, skillIds: List<Long>) {
        val known = store.listSkills().map { it.id }.toSet()
        store.replaceLinks(botUid, skillIds.filter { it in known }, clock.nowSeconds())
    }

    private suspend fun uniqueCopyName(base: String): String {
        val root = SkillNames.slug("$base-copy") ?: "skill-copy"
        var candidate = root
        var suffix = 2
        while (store.getByName(candidate) != null) {
            candidate = SkillNames.slug("$root-$suffix") ?: "$root-$suffix"
            suffix += 1
        }
        return candidate
    }
}
