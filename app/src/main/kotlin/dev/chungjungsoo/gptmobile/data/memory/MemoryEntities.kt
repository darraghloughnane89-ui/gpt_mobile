package dev.chungjungsoo.gptmobile.data.memory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

object MemorySource {
    const val EXPLICIT = "explicit"
    const val SUGGESTION = "suggestion"
}

@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "memory_id")
    val id: Long = 0,
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "paused")
    val paused: Boolean = false,
    /**
     * Reserved for project-scoped memories. Null means the memory applies to every chat.
     */
    @ColumnInfo(name = "project_id")
    val projectId: Long? = null
)

@Entity(tableName = "chat_memory_settings")
data class ChatMemorySetting(
    @PrimaryKey
    @ColumnInfo(name = "chat_id")
    val chatId: Int,
    @ColumnInfo(name = "memory_enabled")
    val memoryEnabled: Boolean
)
