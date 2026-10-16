package dev.chungjungsoo.gptmobile.data.skill

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object SkillOrigin {
    const val USER = "user"
    const val IMPORTED = "imported"
    const val LEARNED = "learned"
}

object SkillProposalAction {
    const val CREATE = "create"
    const val UPDATE = "update"
    const val REFINE = "refine"
    const val RETIRE = "retire"
    const val MEMORY = "memory"
}

object SkillProposalStatus {
    const val PENDING = "pending"
    const val ACCEPTED = "accepted"
    const val REJECTED = "rejected"
}

object SkillProposalSource {
    const val CHAT_END = "chat_end"
    const val SAVE_AS_SKILL = "save_as_skill"
    const val CURATOR = "curator"
}

@Entity(
    tableName = "skills",
    indices = [Index(value = ["stable_name"], unique = true)]
)
data class Skill(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "stable_name")
    val stableName: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "instructions")
    val instructions: String,
    @ColumnInfo(name = "tool_requirements")
    val toolRequirements: String = "",
    @ColumnInfo(name = "example_inputs")
    val exampleInputs: String = "",
    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,
    @ColumnInfo(name = "pinned")
    val pinned: Boolean = false,
    @ColumnInfo(name = "origin")
    val origin: String = SkillOrigin.USER,
    @ColumnInfo(name = "use_count")
    val useCount: Int = 0,
    @ColumnInfo(name = "thumbs_up")
    val thumbsUp: Int = 0,
    @ColumnInfo(name = "thumbs_down")
    val thumbsDown: Int = 0,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

/**
 * Assignment of a skill to a bot. Stream (b) owns the bots table, so this row
 * stores [botUid] without a foreign key. Deleting a skill removes its links.
 */
@Entity(
    tableName = "bot_skill_links",
    primaryKeys = ["bot_uid", "skill_id"],
    foreignKeys = [
        ForeignKey(
            entity = Skill::class,
            parentColumns = ["id"],
            childColumns = ["skill_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["skill_id"])]
)
data class BotSkillLink(
    @ColumnInfo(name = "bot_uid")
    val botUid: String,
    @ColumnInfo(name = "skill_id")
    val skillId: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "skill_proposals",
    indices = [Index(value = ["status"])]
)
data class SkillProposal(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "chat_id")
    val chatId: Int? = null,
    @ColumnInfo(name = "action")
    val action: String,
    @ColumnInfo(name = "target_skill_id")
    val targetSkillId: Long? = null,
    @ColumnInfo(name = "proposed_name")
    val proposedName: String,
    @ColumnInfo(name = "proposed_description")
    val proposedDescription: String,
    @ColumnInfo(name = "proposed_instructions")
    val proposedInstructions: String,
    @ColumnInfo(name = "proposed_tools")
    val proposedTools: String = "",
    @ColumnInfo(name = "proposed_examples")
    val proposedExamples: String = "",
    @ColumnInfo(name = "rationale")
    val rationale: String = "",
    @ColumnInfo(name = "memory_facts_json")
    val memoryFactsJson: String = "[]",
    @ColumnInfo(name = "status")
    val status: String = SkillProposalStatus.PENDING,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null
)

@Entity(tableName = "skill_settings")
data class SkillSettings(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "auto_accept")
    val autoAccept: Boolean = false
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

data class SkillDraft(
    val name: String,
    val title: String,
    val description: String,
    val instructions: String,
    val tools: List<String> = emptyList(),
    val examples: List<String> = emptyList(),
    val pinned: Boolean = false,
    val origin: String = SkillOrigin.USER
)

data class SkillSummary(
    val id: Long,
    val name: String,
    val description: String
)

data class LearningTurn(
    val role: String,
    val text: String
)

data class LearningRequest(
    val chatId: Int,
    val turns: List<LearningTurn>,
    val explicit: Boolean
)

sealed class LearningResult {
    data class Skipped(val explicit: Boolean) : LearningResult()
    data class Proposed(val count: Int, val autoAccepted: Int) : LearningResult()
    data class Failed(val message: String) : LearningResult()
}

sealed class SkillWriteResult {
    data class Ok(val skill: Skill) : SkillWriteResult()
    data class Invalid(val message: String) : SkillWriteResult()
}

sealed class ApplyResult {
    data class Saved(val skillId: Long?) : ApplyResult()
    data class Blocked(val message: String) : ApplyResult()
}
