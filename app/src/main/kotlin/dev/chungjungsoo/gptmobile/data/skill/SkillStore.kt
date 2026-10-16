package dev.chungjungsoo.gptmobile.data.skill

interface SkillStore {
    suspend fun listSkills(): List<Skill>
    suspend fun getSkill(id: Long): Skill?
    suspend fun getByName(name: String): Skill?
    suspend fun insertSkill(skill: Skill): Long
    suspend fun updateSkill(skill: Skill)
    suspend fun deleteSkill(id: Long)
    suspend fun recordUse(id: Long, now: Long)
    suspend fun addThumb(id: Long, up: Boolean, now: Long)
    suspend fun listLinkIds(botUid: String): List<Long>
    suspend fun replaceLinks(botUid: String, skillIds: List<Long>, now: Long)
    suspend fun listProposals(status: String?): List<SkillProposal>
    suspend fun getProposal(id: Long): SkillProposal?
    suspend fun insertProposal(proposal: SkillProposal): Long
    suspend fun updateProposal(proposal: SkillProposal)
    suspend fun getSettings(): SkillSettings
    suspend fun saveSettings(settings: SkillSettings)
}
