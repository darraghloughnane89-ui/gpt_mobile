package dev.chungjungsoo.gptmobile.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "plugins")
data class PluginEntity(
    @PrimaryKey
    @ColumnInfo(name = "plugin_uid")
    val pluginUid: String,
    @ColumnInfo(name = "catalog_id")
    val catalogId: String?,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "endpoint_url")
    val endpointUrl: String,
    @ColumnInfo(name = "transport")
    val transport: String,
    @ColumnInfo(name = "auth_type")
    val authType: String,
    @ColumnInfo(name = "secret_ref")
    val secretRef: String?,
    @ColumnInfo(name = "oauth_client_id")
    val oauthClientId: String?,
    @ColumnInfo(name = "allow_cleartext")
    val allowCleartext: Boolean,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "status_detail")
    val statusDetail: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "plugin_tools",
    foreignKeys = [
        ForeignKey(
            entity = PluginEntity::class,
            parentColumns = ["plugin_uid"],
            childColumns = ["plugin_uid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["plugin_uid"]),
        Index(value = ["plugin_uid", "name"], unique = true)
    ]
)
data class PluginToolEntity(
    @PrimaryKey
    @ColumnInfo(name = "tool_uid")
    val toolUid: String,
    @ColumnInfo(name = "plugin_uid")
    val pluginUid: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "model_name")
    val modelName: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "input_schema_json")
    val inputSchemaJson: String,
    @ColumnInfo(name = "effect")
    val effect: String,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean
)

@Entity(
    tableName = "plugin_scope_selections",
    foreignKeys = [
        ForeignKey(
            entity = PluginEntity::class,
            parentColumns = ["plugin_uid"],
            childColumns = ["plugin_uid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["plugin_uid"]),
        Index(value = ["scope_type", "scope_id", "plugin_uid"], unique = true)
    ]
)
data class PluginScopeSelectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "selection_uid")
    val selectionUid: String,
    @ColumnInfo(name = "scope_type")
    val scopeType: String,
    @ColumnInfo(name = "scope_id")
    val scopeId: String,
    @ColumnInfo(name = "plugin_uid")
    val pluginUid: String
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey
    @ColumnInfo(name = "routine_uid")
    val routineUid: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "prompt")
    val prompt: String,
    @ColumnInfo(name = "bot_uid")
    val botUid: String?,
    @ColumnInfo(name = "platform_uid")
    val platformUid: String,
    @ColumnInfo(name = "schedule_kind")
    val scheduleKind: String,
    @ColumnInfo(name = "cron_expression")
    val cronExpression: String?,
    @ColumnInfo(name = "hour")
    val hour: Int,
    @ColumnInfo(name = "minute")
    val minute: Int,
    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: Int,
    @ColumnInfo(name = "plugin_uids")
    val pluginUids: String,
    @ColumnInfo(name = "allowed_read_tools", defaultValue = "''")
    val allowedReadTools: String = "",
    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
    @ColumnInfo(name = "chat_id")
    val chatId: Int?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "routine_runs",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["routine_uid"],
            childColumns = ["routine_uid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["routine_uid"])]
)
data class RoutineRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "run_uid")
    val runUid: String,
    @ColumnInfo(name = "routine_uid")
    val routineUid: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "result_text")
    val resultText: String?,
    @ColumnInfo(name = "error_text")
    val errorText: String?,
    @ColumnInfo(name = "chat_id")
    val chatId: Int?,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long?
)
