package dev.chungjungsoo.gptmobile.data.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import dev.chungjungsoo.gptmobile.data.security.SecretRedactor
import dev.chungjungsoo.gptmobile.data.security.SecretSyncPolicy
import java.io.File

class SanitizedChatBackupAgent : BackupAgent() {
    override fun onBackup(
        oldState: ParcelFileDescriptor,
        data: BackupDataOutput,
        newState: ParcelFileDescriptor
    ) = Unit

    override fun onRestore(data: BackupDataInput, appVersionCode: Int, newState: ParcelFileDescriptor) = Unit

    override fun onFullBackup(data: FullBackupDataOutput) {
        if (!SanitizedChatBackup.deleteStagedFile(this)) return
        super.onFullBackup(data)
        val source = getDatabasePath(DATABASE_NAME)
        if (!source.exists()) return

        val backup = SanitizedChatBackup.stagedFile(this)
        try {
            SanitizedChatBackup.createSanitizedCopy(source, backup)
            fullBackupFile(backup, data)
        } finally {
            backup.delete()
        }
    }

    private companion object {
        const val DATABASE_NAME = "chat_v2"
    }
}

object SanitizedChatBackup {
    private const val DATABASE_NAME = "chat_v2"
    private const val STAGED_PATH = "backup/chat_v2"

    fun restoreIfPresent(context: Context) {
        val staged = stagedFile(context)
        if (!staged.isFile) return

        val database = context.getDatabasePath(DATABASE_NAME)
        database.parentFile?.mkdirs()
        val atomicFile = AtomicFile(database)
        val output = atomicFile.startWrite()
        try {
            staged.inputStream().use { input -> input.copyTo(output) }
            atomicFile.finishWrite(output)
            File("${database.path}-wal").delete()
            File("${database.path}-shm").delete()
            staged.delete()
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    internal fun createSanitizedCopy(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        destination.delete()
        SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            val escapedDestination = destination.path.replace("'", "''")
            database.execSQL("VACUUM INTO '$escapedDestination'")
        }
        SQLiteDatabase.openDatabase(destination.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            database.execSQL(SecretSyncPolicy.CLEAR_PROVIDER_TOKENS_SQL)
            redactColumn(database, "messages_v2", "content")
            redactColumn(database, "memories", "text")
        }
    }

    private fun redactColumn(database: SQLiteDatabase, table: String, column: String) {
        val exists = database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { cursor -> cursor.moveToFirst() }
        if (!exists) return
        val hasColumn = database.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) found = true
            }
            found
        }
        if (!hasColumn) return
        val updates = mutableListOf<Pair<Long, String>>()
        database.rawQuery("SELECT rowid, $column FROM $table", null).use { cursor ->
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(0)
                val text = cursor.getString(1) ?: continue
                val redacted = SecretRedactor.redact(text)
                if (redacted != text) updates += rowId to redacted
            }
        }
        updates.forEach { (rowId, redacted) ->
            database.execSQL("UPDATE $table SET $column = ? WHERE rowid = ?", arrayOf<Any>(redacted, rowId))
        }
    }

    internal fun deleteStagedFile(context: Context): Boolean {
        val staged = stagedFile(context)
        return !staged.exists() || staged.delete()
    }

    internal fun stagedFile(context: Context): File = File(context.filesDir, STAGED_PATH)
}
