package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.chungjungsoo.gptmobile.data.database.entity.PluginEntity
import dev.chungjungsoo.gptmobile.data.database.entity.PluginScopeSelectionEntity
import dev.chungjungsoo.gptmobile.data.database.entity.PluginToolEntity
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineEntity
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginRoutineDao {
    @Query("SELECT * FROM plugins ORDER BY name, plugin_uid")
    fun observePlugins(): Flow<List<PluginEntity>>

    @Query("SELECT * FROM plugins ORDER BY name, plugin_uid")
    suspend fun listPlugins(): List<PluginEntity>

    @Query("SELECT * FROM plugins WHERE plugin_uid = :pluginUid")
    suspend fun getPlugin(pluginUid: String): PluginEntity?

    @Query("SELECT * FROM plugins WHERE enabled = 1")
    suspend fun enabledPlugins(): List<PluginEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlugin(plugin: PluginEntity)

    @Query("DELETE FROM plugins WHERE plugin_uid = :pluginUid")
    suspend fun deletePlugin(pluginUid: String)

    @Query("UPDATE plugins SET status = :status, status_detail = :detail, updated_at = :updatedAt WHERE plugin_uid = :pluginUid")
    suspend fun updateStatus(pluginUid: String, status: String, detail: String?, updatedAt: Long)

    @Query("UPDATE plugins SET enabled = :enabled, updated_at = :updatedAt WHERE plugin_uid = :pluginUid")
    suspend fun updateEnabled(pluginUid: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE plugins SET oauth_client_id = :clientId, secret_ref = :secretRef, updated_at = :updatedAt WHERE plugin_uid = :pluginUid")
    suspend fun updateCredential(pluginUid: String, clientId: String?, secretRef: String?, updatedAt: Long)

    @Query("SELECT * FROM plugin_tools WHERE plugin_uid = :pluginUid ORDER BY name")
    fun observeTools(pluginUid: String): Flow<List<PluginToolEntity>>

    @Query("SELECT * FROM plugin_tools WHERE plugin_uid = :pluginUid ORDER BY name")
    suspend fun listTools(pluginUid: String): List<PluginToolEntity>

    @Query("DELETE FROM plugin_tools WHERE plugin_uid = :pluginUid")
    suspend fun deleteTools(pluginUid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTools(tools: List<PluginToolEntity>)

    @Query("UPDATE plugin_tools SET enabled = :enabled WHERE tool_uid = :toolUid")
    suspend fun updateToolEnabled(toolUid: String, enabled: Boolean)

    @Query("SELECT * FROM plugin_scope_selections")
    suspend fun listSelections(): List<PluginScopeSelectionEntity>

    @Query("SELECT * FROM plugin_scope_selections WHERE scope_type = :scopeType AND scope_id = :scopeId")
    suspend fun selectionsFor(scopeType: String, scopeId: String): List<PluginScopeSelectionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSelection(selection: PluginScopeSelectionEntity)

    @Query("DELETE FROM plugin_scope_selections WHERE scope_type = :scopeType AND scope_id = :scopeId AND plugin_uid = :pluginUid")
    suspend fun deleteSelection(scopeType: String, scopeId: String, pluginUid: String)

    @Query("SELECT * FROM routines ORDER BY title, routine_uid")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE enabled = 1")
    suspend fun enabledRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE routine_uid = :routineUid")
    suspend fun getRoutine(routineUid: String): RoutineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE routine_uid = :routineUid")
    suspend fun deleteRoutine(routineUid: String)

    @Query("UPDATE routines SET chat_id = :chatId, updated_at = :updatedAt WHERE routine_uid = :routineUid")
    suspend fun updateRoutineChat(routineUid: String, chatId: Int, updatedAt: Long)

    @Query("SELECT * FROM routine_runs WHERE routine_uid = :routineUid ORDER BY started_at DESC")
    fun observeRuns(routineUid: String): Flow<List<RoutineRunEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RoutineRunEntity)
}
