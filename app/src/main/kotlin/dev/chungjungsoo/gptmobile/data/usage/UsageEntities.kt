package dev.chungjungsoo.gptmobile.data.usage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_events")
data class UsageEvent(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "usage_id")
    val id: Long = 0,
    @ColumnInfo(name = "platform_uid")
    val platformUid: String,
    @ColumnInfo(name = "model")
    val model: String,
    @ColumnInfo(name = "chat_id")
    val chatId: Int?,
    @ColumnInfo(name = "input_tokens")
    val inputTokens: Int,
    @ColumnInfo(name = "output_tokens")
    val outputTokens: Int,
    @ColumnInfo(name = "estimated")
    val estimated: Boolean,
    @ColumnInfo(name = "cost_micros")
    val costMicros: Long,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(tableName = "model_prices")
data class ModelPrice(
    @PrimaryKey
    @ColumnInfo(name = "model_key")
    val modelKey: String,
    @ColumnInfo(name = "input_micros_per_million")
    val inputMicrosPerMillion: Long,
    @ColumnInfo(name = "output_micros_per_million")
    val outputMicrosPerMillion: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
