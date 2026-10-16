package dev.chungjungsoo.gptmobile.data.drive

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drive_sync_state")
data class DriveSyncState(
    @PrimaryKey
    @ColumnInfo(name = "sync_id")
    val syncId: Int = DriveBackupSpec.SYNC_ROW,
    @ColumnInfo(name = "drive_file_id")
    val driveFileId: String? = null,
    @ColumnInfo(name = "last_backup_at")
    val lastBackupAt: Long? = null,
    @ColumnInfo(name = "last_restore_at")
    val lastRestoreAt: Long? = null,
    @ColumnInfo(name = "content_hash")
    val contentHash: String? = null
)
