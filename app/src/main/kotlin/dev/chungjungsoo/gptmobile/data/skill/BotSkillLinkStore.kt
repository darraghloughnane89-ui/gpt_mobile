package dev.chungjungsoo.gptmobile.data.skill

/**
 * Bot-to-skill assignments. Stream (b) can call this once a bot uid exists.
 * The link table does not require the bots table to be present.
 */
interface BotSkillLinkStore {
    suspend fun skillsForBot(botUid: String): List<Skill>
    suspend fun linkSkill(botUid: String, skillId: Long)
    suspend fun unlinkSkill(botUid: String, skillId: Long)
    suspend fun replaceBotSkills(botUid: String, skillIds: List<Long>)
}
