package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.data.database.entity.BotToolBinding

@Dao
interface BotDao {
    @Query("SELECT * FROM bots ORDER BY builtin DESC, name COLLATE NOCASE ASC, bot_uid ASC")
    suspend fun listBots(): List<Bot>

    @Query("SELECT * FROM bots WHERE bot_uid = :botUid")
    suspend fun getBot(botUid: String): Bot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBot(bot: Bot)

    @Query("DELETE FROM bots WHERE bot_uid = :botUid")
    suspend fun deleteBot(botUid: String)

    @Query("UPDATE chats_v2 SET bot_uid = :botUid WHERE chat_id = :chatId")
    suspend fun updateChatBot(chatId: Int, botUid: String?)

    @Query("UPDATE chats_v2 SET bot_uid = NULL WHERE bot_uid = :botUid")
    suspend fun clearChatBot(botUid: String)

    @Query("SELECT * FROM bot_tool_bindings WHERE bot_uid = :botUid ORDER BY tool_name, connection_uid, binding_uid")
    suspend fun listBindings(botUid: String): List<BotToolBinding>

    @Query("DELETE FROM bot_tool_bindings WHERE bot_uid = :botUid")
    suspend fun deleteBindings(botUid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBindings(bindings: List<BotToolBinding>)

    @Transaction
    suspend fun save(bot: Bot, bindings: List<BotToolBinding>) {
        upsertBot(bot)
        deleteBindings(bot.botUid)
        if (bindings.isNotEmpty()) {
            insertBindings(bindings)
        }
    }
}
