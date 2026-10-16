package dev.chungjungsoo.gptmobile.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DriveMigration {
    val CREATE_DRIVE_SYNC_STATE = """
        CREATE TABLE IF NOT EXISTS `drive_sync_state` (
            `sync_id` INTEGER NOT NULL,
            `drive_file_id` TEXT,
            `last_backup_at` INTEGER,
            `last_restore_at` INTEGER,
            `content_hash` TEXT,
            PRIMARY KEY(`sync_id`)
        )
    """.trimIndent()

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_DRIVE_SYNC_STATE)
        }
    }
}
