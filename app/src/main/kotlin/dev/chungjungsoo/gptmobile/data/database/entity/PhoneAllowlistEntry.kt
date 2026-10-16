package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "phone_allowlist")
data class PhoneAllowlistEntry(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "allowed")
    val allowed: Boolean,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
