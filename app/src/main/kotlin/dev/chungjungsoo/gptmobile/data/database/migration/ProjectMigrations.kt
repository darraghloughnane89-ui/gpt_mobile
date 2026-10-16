package dev.chungjungsoo.gptmobile.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Projects schema, Room version 13.
 *
 * Installed upgrades go 11 → 12 (bots) → 13 (projects). [MIGRATION_12_13] is the
 * migration registered with Room. It only adds project tables and
 * `chats_v2.project_id`, so it does not depend on the bots columns from version 12.
 *
 * [MIGRATION_11_13] is not registered. Room would prefer that direct path and skip
 * the bots tables. It remains so the project SQL can be compared with [MIGRATION_12_13].
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
            `updated_at` INTEGER NOT NULL,
            `import_key` TEXT
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_projects_import_key` ON `projects` (`import_key`)",
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
