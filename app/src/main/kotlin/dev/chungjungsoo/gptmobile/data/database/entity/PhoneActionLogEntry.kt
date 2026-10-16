package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "phone_action_log")
data class PhoneActionLogEntry(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "outcome")
    val outcome: String,

    @ColumnInfo(name = "confirmed")
    val confirmed: Boolean
)
