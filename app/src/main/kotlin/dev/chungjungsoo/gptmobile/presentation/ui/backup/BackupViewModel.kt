package dev.chungjungsoo.gptmobile.presentation.ui.backup

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.backup.ChatSnapshotSource
import dev.chungjungsoo.gptmobile.data.drive.DriveAuthorization
import dev.chungjungsoo.gptmobile.data.drive.DriveAuthorizer
import dev.chungjungsoo.gptmobile.data.drive.DriveBackupCoordinator
import dev.chungjungsoo.gptmobile.data.drive.DriveBackupResult
import dev.chungjungsoo.gptmobile.data.security.VaultSession
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DriveAction {
    BACKUP,
    RESTORE
}

data class BackupUiState(
    val busy: Boolean = false,
    val notice: String = "",
    val noticeIsError: Boolean = false,
    val consentIntent: PendingIntent? = null,
    val unlockRequested: Boolean = false
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val snapshots: ChatSnapshotSource,
    private val coordinator: DriveBackupCoordinator,
    private val authorizer: DriveAuthorizer,
    private val session: VaultSession
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state = _state.asStateFlow()
    private var pendingAction: DriveAction? = null
    private var unlockAction: DriveAction? = null

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, notice = "", noticeIsError = false) }
            val outcome = try {
                val json = snapshots.exportJson()
                val stream = context.contentResolver.openOutputStream(uri)
                if (stream == null) {
                    Outcome(context.getString(R.string.backup_failed, "Unable to open the export file."), true)
                } else {
                    stream.use { output -> output.write(json.toByteArray(Charsets.UTF_8)) }
                    Outcome(context.getString(R.string.backup_exported), false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Outcome(context.getString(R.string.backup_failed, error.message ?: ""), true)
            }
            _state.update { it.copy(busy = false, notice = outcome.text, noticeIsError = outcome.error) }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, notice = "", noticeIsError = false) }
            val outcome = try {
                val text = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
                if (text == null) {
                    Outcome(context.getString(R.string.backup_failed, "Unable to open the import file."), true)
                } else {
                    val counts = snapshots.importJson(text)
                    Outcome(context.getString(R.string.backup_imported, counts.chatsAdded, counts.memoriesWritten), false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Outcome(context.getString(R.string.backup_failed, error.message ?: ""), true)
            }
            _state.update { it.copy(busy = false, notice = outcome.text, noticeIsError = outcome.error) }
        }
    }

    fun backupToDrive() = beginDrive(DriveAction.BACKUP)

    fun restoreFromDrive() = beginDrive(DriveAction.RESTORE)

    fun consentDelivered() {
        _state.update { it.copy(consentIntent = null) }
    }

    fun onConsentFinished(success: Boolean) {
        val action = pendingAction
        if (!success || action == null) {
            pendingAction = null
            _state.update {
                it.copy(
                    busy = false,
                    consentIntent = null,
                    notice = context.getString(R.string.backup_consent_cancelled),
                    noticeIsError = true
                )
            }
            return
        }
        startDrive(action, allowConsent = false)
    }

    fun onVaultUnlocked(success: Boolean) {
        val action = unlockAction
        unlockAction = null
        if (!success || action == null) {
            _state.update {
                it.copy(
                    unlockRequested = false,
                    busy = false,
                    notice = context.getString(R.string.backup_consent_cancelled),
                    noticeIsError = true
                )
            }
            return
        }
        session.unlock()
        _state.update { it.copy(unlockRequested = false) }
        startDrive(action, allowConsent = true)
    }

    private fun beginDrive(action: DriveAction) {
        if (!session.isUnlocked()) {
            unlockAction = action
            _state.update {
                it.copy(
                    busy = true,
                    unlockRequested = true,
                    notice = context.getString(R.string.backup_unlock_required),
                    noticeIsError = false
                )
            }
            return
        }
        startDrive(action, allowConsent = true)
    }

    private fun startDrive(action: DriveAction, allowConsent: Boolean) {
        pendingAction = action
        viewModelScope.launch {
            _state.update { it.copy(busy = true, notice = "", noticeIsError = false, consentIntent = null) }
            val authorization = authorizer.authorize()
            when (authorization) {
                is DriveAuthorization.NeedsConsent -> {
                    val intent = authorizer.consentIntent()
                    if (allowConsent && intent != null) {
                        _state.update { it.copy(busy = false, consentIntent = intent) }
                    } else {
                        pendingAction = null
                        _state.update {
                            it.copy(
                                busy = false,
                                notice = context.getString(R.string.backup_consent_cancelled),
                                noticeIsError = true
                            )
                        }
                    }
                }
                is DriveAuthorization.NotConfigured -> show(authorization.reason, error = true)
                is DriveAuthorization.Failed -> show(authorization.reason, error = true)
                is DriveAuthorization.Ready -> {
                    val result = when (action) {
                        DriveAction.BACKUP -> coordinator.backup(authorization)
                        DriveAction.RESTORE -> coordinator.restore(authorization)
                    }
                    showResult(result)
                }
            }
        }
    }

    private fun showResult(result: DriveBackupResult) {
        pendingAction = null
        val outcome = when (result) {
            is DriveBackupResult.BackedUp -> Outcome(context.getString(R.string.backup_drive_done), false)
            is DriveBackupResult.Restored -> Outcome(
                context.getString(R.string.backup_drive_restored, result.chats, result.memories),
                false
            )
            is DriveBackupResult.NotConfigured -> Outcome(result.reason, true)
            DriveBackupResult.NeedsConsent -> Outcome(context.getString(R.string.backup_consent_cancelled), true)
            DriveBackupResult.Locked -> Outcome(context.getString(R.string.backup_locked), true)
            is DriveBackupResult.Failed -> Outcome(context.getString(R.string.backup_failed, result.message), true)
        }
        show(outcome.text, outcome.error)
    }

    private fun show(text: String, error: Boolean) {
        pendingAction = null
        _state.update { it.copy(busy = false, notice = text, noticeIsError = error, consentIntent = null) }
    }

    private data class Outcome(val text: String, val error: Boolean)
}
