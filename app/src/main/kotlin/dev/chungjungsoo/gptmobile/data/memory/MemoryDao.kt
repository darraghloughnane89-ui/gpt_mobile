package dev.chungjungsoo.gptmobile.data.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<Memory>>

    @Query("SELECT * FROM memories WHERE paused = 0 ORDER BY updated_at DESC")
    suspend fun getActive(): List<Memory>

    @Query("SELECT * FROM memories WHERE memory_id = :id LIMIT 1")
    suspend fun getById(id: Long): Memory?

    @Query("SELECT * FROM memories WHERE text = :text COLLATE NOCASE LIMIT 1")
    suspend fun findByText(text: String): Memory?

    @Insert
    suspend fun insert(memory: Memory): Long

    @Update
    suspend fun update(memory: Memory)

    @Query("DELETE FROM memories WHERE memory_id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()

    @Query("SELECT memory_enabled FROM chat_memory_settings WHERE chat_id = :chatId")
    suspend fun memoryEnabled(chatId: Int): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatSetting(setting: ChatMemorySetting)
}
