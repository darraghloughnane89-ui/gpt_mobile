package dev.chungjungsoo.gptmobile.data.database.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "chats_v2",
    indices = [
        Index(value = ["bot_uid"])
    ]
)
data class ChatRoomV2(
    /**
     Now, enabled platforms are stored as list of strings.
     The strings are UUID V4 strings from PlatformV2.uid
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "chat_id")
    val id: Int = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "enabled_platform")
    val enabledPlatform: List<String>,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis() / 1000,

    @ColumnInfo(name = "bot_uid")
    val botUid: String? = null
) : Parcelable

class StringListConverter {
    @TypeConverter
    fun fromString(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(",")

    @TypeConverter
    fun fromList(value: List<String>): String = if (value.isEmpty()) "" else value.joinToString(",")
}
