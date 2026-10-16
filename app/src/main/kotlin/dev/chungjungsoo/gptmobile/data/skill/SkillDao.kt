package dev.chungjungsoo.gptmobile.data.skill

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface SkillDao {
    @Query("SELECT * FROM skills ORDER BY title COLLATE NOCASE")
    suspend fun listSkills(): List<Skill>

    @Query("SELECT * FROM skills WHERE id = :id")
    suspend fun getSkill(id: Long): Skill?

    @Query("SELECT * FROM skills WHERE stable_name = :name")
    suspend fun getByName(name: String): Skill?

    @Insert
    suspend fun insertSkill(skill: Skill): Long

    @Update
    suspend fun updateSkill(skill: Skill)

    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun deleteSkill(id: Long)

    @Query("UPDATE skills SET use_count = use_count + 1, last_used_at = :now, updated_at = :now WHERE id = :id")
    suspend fun recordUse(id: Long, now: Long)

    @Query("UPDATE skills SET thumbs_up = thumbs_up + 1, updated_at = :now WHERE id = :id")
    suspend fun addThumbUp(id: Long, now: Long)

    @Query("UPDATE skills SET thumbs_down = thumbs_down + 1, updated_at = :now WHERE id = :id")
    suspend fun addThumbDown(id: Long, now: Long)

    @Query("SELECT skill_id FROM bot_skill_links WHERE bot_uid = :botUid ORDER BY skill_id")
    suspend fun listLinkIds(botUid: String): List<Long>

    @Query("DELETE FROM bot_skill_links WHERE bot_uid = :botUid")
    suspend fun deleteLinks(botUid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLinks(links: List<BotSkillLink>)

    @Transaction
    suspend fun replaceLinks(botUid: String, links: List<BotSkillLink>) {
        deleteLinks(botUid)
        if (links.isNotEmpty()) insertLinks(links)
    }

    @Query("SELECT * FROM skill_proposals WHERE status = :status ORDER BY created_at DESC")
    suspend fun listProposals(status: String): List<SkillProposal>

    @Query("SELECT * FROM skill_proposals ORDER BY created_at DESC")
    suspend fun listAllProposals(): List<SkillProposal>

    @Query("SELECT * FROM skill_proposals WHERE id = :id")
    suspend fun getProposal(id: Long): SkillProposal?

    @Insert
    suspend fun insertProposal(proposal: SkillProposal): Long

    @Update
    suspend fun updateProposal(proposal: SkillProposal)

    @Query("SELECT * FROM skill_settings WHERE id = :id")
    suspend fun getSettings(id: Int): SkillSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: SkillSettings)
}
