package dev.chungjungsoo.gptmobile.data.skill

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSkillStore @Inject constructor(
    private val dao: SkillDao
) : SkillStore {
    override suspend fun listSkills(): List<Skill> = dao.listSkills()

    override suspend fun getSkill(id: Long): Skill? = dao.getSkill(id)

    override suspend fun getByName(name: String): Skill? = dao.getByName(name)

    override suspend fun insertSkill(skill: Skill): Long = dao.insertSkill(skill)

    override suspend fun updateSkill(skill: Skill) = dao.updateSkill(skill)

    override suspend fun deleteSkill(id: Long) = dao.deleteSkill(id)

    override suspend fun recordUse(id: Long, now: Long) = dao.recordUse(id, now)

    override suspend fun addThumb(id: Long, up: Boolean, now: Long) {
        if (up) dao.addThumbUp(id, now) else dao.addThumbDown(id, now)
    }

    override suspend fun listLinkIds(botUid: String): List<Long> = dao.listLinkIds(botUid)

    override suspend fun replaceLinks(botUid: String, skillIds: List<Long>, now: Long) {
        dao.replaceLinks(
            botUid,
            skillIds.distinct().map { skillId -> BotSkillLink(botUid, skillId, now) }
        )
    }

    override suspend fun listProposals(status: String?): List<SkillProposal> = if (status == null) {
        dao.listAllProposals()
    } else {
        dao.listProposals(status)
    }

    override suspend fun getProposal(id: Long): SkillProposal? = dao.getProposal(id)

    override suspend fun insertProposal(proposal: SkillProposal): Long = dao.insertProposal(proposal)

    override suspend fun updateProposal(proposal: SkillProposal) = dao.updateProposal(proposal)

    override suspend fun getSettings(): SkillSettings = dao.getSettings(SkillSettings.SINGLETON_ID) ?: SkillSettings()

    override suspend fun saveSettings(settings: SkillSettings) = dao.upsertSettings(settings)
}
