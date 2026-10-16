package dev.chungjungsoo.gptmobile.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import dev.chungjungsoo.gptmobile.data.database.entity.PhoneActionLogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.PhoneAllowlistEntry
import dev.chungjungsoo.gptmobile.data.database.entity.PhoneRoutine
import dev.chungjungsoo.gptmobile.data.database.entity.RobinVaultMeta
import kotlinx.coroutines.flow.Flow

@Dao
interface PhoneControlDao {
    @Query("SELECT * FROM phone_allowlist ORDER BY label")
    fun observeAllowlist(): Flow<List<PhoneAllowlistEntry>>

    @Upsert
    suspend fun upsertAllowlist(entry: PhoneAllowlistEntry)

    @Query("SELECT package_name FROM phone_allowlist WHERE allowed = 1")
    suspend fun allowedPackages(): List<String>

    @Insert
    suspend fun insertLog(entry: PhoneActionLogEntry)

    @Query("SELECT * FROM phone_action_log ORDER BY created_at DESC LIMIT 200")
    fun observeLog(): Flow<List<PhoneActionLogEntry>>

    @Query("SELECT * FROM phone_routines WHERE approved = 1")
    suspend fun approvedRoutines(): List<PhoneRoutine>

    @Upsert
    suspend fun upsertRoutine(routine: PhoneRoutine)

    @Query("SELECT * FROM robin_vault_meta ORDER BY created_at DESC")
    suspend fun vaultMeta(): List<RobinVaultMeta>

    @Upsert
    suspend fun upsertVaultMeta(meta: RobinVaultMeta)

    @Query("DELETE FROM robin_vault_meta WHERE id = :id")
    suspend fun deleteVaultMeta(id: String)
}
