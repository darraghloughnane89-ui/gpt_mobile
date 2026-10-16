package dev.chungjungsoo.gptmobile.data.skill

class MemorySkillStore : SkillStore {
    private val skills = linkedMapOf<Long, Skill>()
    private val links = linkedMapOf<String, List<Long>>()
    private val proposals = linkedMapOf<Long, SkillProposal>()
    private var settings = SkillSettings()
    private var nextSkillId = 1L
    private var nextProposalId = 1L

    override suspend fun listSkills(): List<Skill> = skills.values.sortedBy { it.title.lowercase() }

    override suspend fun getSkill(id: Long): Skill? = skills[id]

    override suspend fun getByName(name: String): Skill? = skills.values.firstOrNull { it.stableName == name }

    override suspend fun insertSkill(skill: Skill): Long {
        val id = if (skill.id == 0L) nextSkillId++ else skill.id
        skills[id] = skill.copy(id = id)
        if (id >= nextSkillId) nextSkillId = id + 1
        return id
    }

    override suspend fun updateSkill(skill: Skill) {
        skills[skill.id] = skill
    }

    override suspend fun deleteSkill(id: Long) {
        skills.remove(id)
        links.keys.forEach { botUid ->
            links[botUid] = links[botUid].orEmpty().filter { it != id }
        }
    }

    override suspend fun recordUse(id: Long, now: Long) {
        val skill = skills[id] ?: return
        skills[id] = skill.copy(useCount = skill.useCount + 1, lastUsedAt = now, updatedAt = now)
    }

    override suspend fun addThumb(id: Long, up: Boolean, now: Long) {
        val skill = skills[id] ?: return
        skills[id] = if (up) {
            skill.copy(thumbsUp = skill.thumbsUp + 1, updatedAt = now)
        } else {
            skill.copy(thumbsDown = skill.thumbsDown + 1, updatedAt = now)
        }
    }

    override suspend fun listLinkIds(botUid: String): List<Long> = links[botUid].orEmpty()

    override suspend fun replaceLinks(botUid: String, skillIds: List<Long>, now: Long) {
        links[botUid] = skillIds.distinct()
    }

    override suspend fun listProposals(status: String?): List<SkillProposal> = proposals.values
        .filter { status == null || it.status == status }
        .sortedByDescending { it.createdAt }

    override suspend fun getProposal(id: Long): SkillProposal? = proposals[id]

    override suspend fun insertProposal(proposal: SkillProposal): Long {
        val id = if (proposal.id == 0L) nextProposalId++ else proposal.id
        proposals[id] = proposal.copy(id = id)
        if (id >= nextProposalId) nextProposalId = id + 1
        return id
    }

    override suspend fun updateProposal(proposal: SkillProposal) {
        proposals[proposal.id] = proposal
    }

    override suspend fun getSettings(): SkillSettings = settings

    override suspend fun saveSettings(settings: SkillSettings) {
        this.settings = settings
    }
}

class FixedSkillClock(var now: Long = 1_700_000_000L) : SkillClock {
    override fun nowSeconds(): Long = now
}

class FakeSkillReviewer(var response: String) : SkillReviewer {
    var calls: Int = 0
    override suspend fun review(system: String, user: String): String {
        calls += 1
        return response
    }
}

class RecordingMemoryWriter : MemoryWriter {
    val facts = mutableListOf<MemoryFact>()
    override suspend fun write(facts: List<MemoryFact>): MemoryWriteResult {
        this.facts += facts
        return MemoryWriteResult.Written(facts.size)
    }
}
