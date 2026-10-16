package dev.chungjungsoo.gptmobile.data.phonecontrol

import dev.chungjungsoo.gptmobile.data.database.dao.PhoneControlDao
import dev.chungjungsoo.gptmobile.data.database.entity.PhoneActionLogEntry
import dev.chungjungsoo.gptmobile.data.database.entity.PhoneAllowlistEntry
import dev.chungjungsoo.gptmobile.data.database.entity.PhoneRoutine
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.Redaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class PhoneControlRepository @Inject constructor(
    private val dao: PhoneControlDao
) {
    fun observeAllowlist(): Flow<List<PhoneAllowlistEntry>> = dao.observeAllowlist()

    fun observeLog(): Flow<List<PhoneActionLogEntry>> = dao.observeLog()

    suspend fun allowedPackages(): Set<String> = dao.allowedPackages().toSet()

    suspend fun setAllowed(packageName: String, label: String, allowed: Boolean) {
        dao.upsertAllowlist(
            PhoneAllowlistEntry(
                packageName = packageName,
                label = label,
                allowed = allowed,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun log(packageName: String, action: String, summary: String, outcome: String, confirmed: Boolean) {
        dao.insertLog(
            PhoneActionLogEntry(
                id = java.util.UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                packageName = packageName,
                action = action,
                summary = Redaction.redactLog(summary),
                outcome = Redaction.redactLog(outcome),
                confirmed = confirmed
            )
        )
    }

    suspend fun hasApprovedRoutine(): Boolean = dao.approvedRoutines().isNotEmpty()

    suspend fun setRoutine(name: String, approved: Boolean) {
        dao.upsertRoutine(
            PhoneRoutine(
                id = "default",
                name = name,
                approved = approved,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
