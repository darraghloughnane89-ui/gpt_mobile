package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bot_tool_bindings",
    foreignKeys = [
        ForeignKey(
            entity = Bot::class,
            parentColumns = ["bot_uid"],
            childColumns = ["bot_uid"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ToolConnection::class,
            parentColumns = ["connection_uid"],
            childColumns = ["connection_uid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bot_uid"]),
        Index(value = ["connection_uid"]),
        Index(value = ["bot_uid", "connection_uid", "tool_name"], unique = true)
    ]
)
data class BotToolBinding(
    @PrimaryKey
    @ColumnInfo(name = "binding_uid")
    val bindingUid: String,

    @ColumnInfo(name = "bot_uid")
    val botUid: String,

    @ColumnInfo(name = "connection_uid")
    val connectionUid: String?,

    @ColumnInfo(name = "tool_name")
    val toolName: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0
) {
    fun toAgentToolBinding(): AgentToolBinding = AgentToolBinding(
        bindingUid = bindingUid,
        profileUid = botUid,
        connectionUid = connectionUid,
        toolName = toolName,
        createdAt = createdAt
    )
}
