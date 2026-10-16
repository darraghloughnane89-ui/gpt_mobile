package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.chungjungsoo.gptmobile.data.bot.StarterPrompts

@Entity(tableName = "bots")
data class Bot(
    @PrimaryKey
    @ColumnInfo(name = "bot_uid")
    val botUid: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "avatar_emoji")
    val avatarEmoji: String? = null,

    @ColumnInfo(name = "avatar_color")
    val avatarColor: String? = null,

    @ColumnInfo(name = "instructions")
    val instructions: String,

    @ColumnInfo(name = "default_platform_uid")
    val defaultPlatformUid: String? = null,

    @ColumnInfo(name = "default_model")
    val defaultModel: String? = null,

    @ColumnInfo(name = "starter_prompts")
    val starterPrompts: String = "[]",

    @ColumnInfo(name = "builtin")
    val builtin: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0
) {
    fun starterPromptList(): List<String> = StarterPrompts.decode(starterPrompts)

    fun modelOverride(enabledPlatformUids: Collection<String>): Pair<String, String>? {
        val platformUid = defaultPlatformUid?.takeIf { it in enabledPlatformUids } ?: return null
        val model = defaultModel?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return platformUid to model
    }
}
