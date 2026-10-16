package dev.chungjungsoo.gptmobile.data.drive

import dev.chungjungsoo.gptmobile.data.backup.BackupCipher
import dev.chungjungsoo.gptmobile.data.backup.BackupCipherException
import dev.chungjungsoo.gptmobile.data.backup.ChatArchiveCodec
import dev.chungjungsoo.gptmobile.data.backup.ChatSnapshotSource
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import dev.chungjungsoo.gptmobile.data.security.VaultLockedException
import dev.chungjungsoo.gptmobile.data.security.VaultSession
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

sealed class DriveBackupResult {
    data class BackedUp(val fileId: String) : DriveBackupResult()
    data class Restored(val chats: Int, val memories: Int) : DriveBackupResult()
    data class NotConfigured(val reason: String) : DriveBackupResult()
    data object NeedsConsent : DriveBackupResult()
    data object Locked : DriveBackupResult()
    data class Failed(val message: String) : DriveBackupResult()
}

@Singleton
class DriveBackupCoordinator @Inject constructor(
    private val snapshots: ChatSnapshotSource,
    private val syncDao: DriveSyncDao,
    private val transport: DriveAppDataStore,
    private val vault: SecretVault,
    private val session: VaultSession
) {
    suspend fun backup(authorization: DriveAuthorization): DriveBackupResult = when (authorization) {
        is DriveAuthorization.NotConfigured -> DriveBackupResult.NotConfigured(authorization.reason)
        is DriveAuthorization.Failed -> DriveBackupResult.Failed(authorization.reason)
        DriveAuthorization.NeedsConsent -> DriveBackupResult.NeedsConsent
        is DriveAuthorization.Ready -> encryptAndUpload(authorization.accessToken)
    }

    suspend fun restore(authorization: DriveAuthorization): DriveBackupResult = when (authorization) {
        is DriveAuthorization.NotConfigured -> DriveBackupResult.NotConfigured(authorization.reason)
        is DriveAuthorization.Failed -> DriveBackupResult.Failed(authorization.reason)
        DriveAuthorization.NeedsConsent -> DriveBackupResult.NeedsConsent
        is DriveAuthorization.Ready -> downloadAndImport(authorization.accessToken)
    }

    private suspend fun encryptAndUpload(accessToken: String): DriveBackupResult {
        if (!session.isUnlocked()) return DriveBackupResult.Locked
        return try {
            val plaintext = ChatArchiveCodec.encode(snapshots.snapshot()).encodeToByteArray()
            val key = backupKey(create = true)
            val encrypted = try {
                BackupCipher.encrypt(key, plaintext)
            } finally {
                key.fill(0)
                plaintext.fill(0)
            }
            val state = syncDao.get()
            val existing = state?.driveFileId ?: transport.findBackup(accessToken)
            val fileId = transport.upload(accessToken, existing, encrypted)
            syncDao.upsert(
                DriveSyncState(
                    driveFileId = fileId,
                    lastBackupAt = System.currentTimeMillis() / 1000,
                    lastRestoreAt = state?.lastRestoreAt,
                    contentHash = sha256(encrypted)
                )
            )
            DriveBackupResult.BackedUp(fileId)
        } catch (locked: VaultLockedException) {
            DriveBackupResult.Locked
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DriveBackupResult.Failed(error.message ?: "Drive backup failed.")
        }
    }

    private suspend fun downloadAndImport(accessToken: String): DriveBackupResult {
        if (!session.isUnlocked()) return DriveBackupResult.Locked
        return try {
            val state = syncDao.get()
            val fileId = state?.driveFileId ?: transport.findBackup(accessToken)
                ?: return DriveBackupResult.Failed("No Drive backup found.")
            val encrypted = transport.download(accessToken, fileId)
            val key = backupKey(create = false)
            val plain = try {
                BackupCipher.decrypt(key, encrypted)
            } finally {
                key.fill(0)
            }
            val archive = ChatArchiveCodec.decode(plain.toString(Charsets.UTF_8))
            plain.fill(0)
            val counts = snapshots.importAdditive(archive)
            syncDao.upsert(
                DriveSyncState(
                    driveFileId = fileId,
                    lastBackupAt = state?.lastBackupAt,
                    lastRestoreAt = System.currentTimeMillis() / 1000,
                    contentHash = sha256(encrypted)
                )
            )
            DriveBackupResult.Restored(counts.chatsAdded, counts.memoriesWritten)
        } catch (locked: VaultLockedException) {
            DriveBackupResult.Locked
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DriveBackupResult.Failed(error.message ?: "Drive restore failed.")
        }
    }

    private suspend fun backupKey(create: Boolean): ByteArray {
        if (!session.isUnlocked()) throw VaultLockedException()
        val existing = vault.read(DriveBackupSpec.KEY_REF)
        if (existing != null && existing.size == 32) return existing
        existing?.fill(0)
        if (!create) {
            throw BackupCipherException(
                "No backup key on this device. Import a JSON export instead."
            )
        }
        val created = ByteArray(32)
        SecureRandom().nextBytes(created)
        vault.put(DriveBackupSpec.KEY_REF, created)
        return created
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
