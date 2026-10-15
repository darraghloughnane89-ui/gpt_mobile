package dev.chungjungsoo.gptmobile.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Projects schema, reserved as Room version 13.
 *
 * [MIGRATION_12_13] is the migration to keep. It only adds project tables and
 * `chats_v2.project_id`, so it does not depend on the bots columns from version 12.
 *
 * [MIGRATION_11_13] is temporary. This branch starts at version 11, before the bots
 * stream's 11→12 migration exists. Register it so existing installs can open.
 * After bots lands, drop [MIGRATION_11_13] and leave [MIGRATION_12_13].
 */
object ProjectMigrations {
    val PROJECT_SCHEMA_SQL = listOf(
        """
        CREATE TABLE IF NOT EXISTS `projects` (
            `project_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `icon` TEXT NOT NULL,
            `color` INTEGER NOT NULL,
            `instructions` TEXT NOT NULL,
            `created_at` INTEGER NOT NULL,
            `updated_at` INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS `project_files` (
            `file_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `project_id` INTEGER NOT NULL,
            `display_name` TEXT NOT NULL,
            `mime_type` TEXT NOT NULL,
            `storage_name` TEXT NOT NULL,
            `extracted_text` TEXT,
            `size_bytes` INTEGER NOT NULL,
            `created_at` INTEGER NOT NULL,
            FOREIGN KEY(`project_id`) REFERENCES `projects`(`project_id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_project_files_project_id` ON `project_files` (`project_id`)",
        "ALTER TABLE `chats_v2` ADD COLUMN `project_id` INTEGER",
        "CREATE INDEX IF NOT EXISTS `index_chats_v2_project_id` ON `chats_v2` (`project_id`)"
    )

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) = db.applyProjectSchema()
    }

    val MIGRATION_11_13 = object : Migration(11, 13) {
        override fun migrate(db: SupportSQLiteDatabase) = db.applyProjectSchema()
    }

    private fun SupportSQLiteDatabase.applyProjectSchema() {
        PROJECT_SCHEMA_SQL.forEach(::execSQL)
    }
}
