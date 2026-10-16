package dev.chungjungsoo.gptmobile.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room version 18, owned by the skills stream.
 * Migrations 1–11 stay in [dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2Migrations].
 * Versions 12–17 are reserved by other streams. This branch starts from version 11,
 * so the registered path is 11 → 18. If 12–17 land first, rebase this file to start at 17
 * and keep [STATEMENTS] the same.
 */
object SkillSchemaMigration {
    const val FROM = 11
    const val TO = 18

    val STATEMENTS = listOf(
        """
        CREATE TABLE IF NOT EXISTS `skills` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `stable_name` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `description` TEXT NOT NULL,
            `instructions` TEXT NOT NULL,
            `tool_requirements` TEXT NOT NULL,
            `example_inputs` TEXT NOT NULL,
            `enabled` INTEGER NOT NULL,
            `pinned` INTEGER NOT NULL,
            `origin` TEXT NOT NULL,
            `use_count` INTEGER NOT NULL,
            `thumbs_up` INTEGER NOT NULL,
            `thumbs_down` INTEGER NOT NULL,
            `last_used_at` INTEGER,
            `created_at` INTEGER NOT NULL,
            `updated_at` INTEGER NOT NULL
        )
        """.trimIndent(),
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_skills_stable_name` ON `skills` (`stable_name`)",
        """
        CREATE TABLE IF NOT EXISTS `bot_skill_links` (
            `bot_uid` TEXT NOT NULL,
            `skill_id` INTEGER NOT NULL,
            `created_at` INTEGER NOT NULL,
            PRIMARY KEY(`bot_uid`, `skill_id`),
            FOREIGN KEY(`skill_id`) REFERENCES `skills`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_bot_skill_links_skill_id` ON `bot_skill_links` (`skill_id`)",
        """
        CREATE TABLE IF NOT EXISTS `skill_proposals` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `chat_id` INTEGER,
            `action` TEXT NOT NULL,
            `target_skill_id` INTEGER,
            `proposed_name` TEXT NOT NULL,
            `proposed_description` TEXT NOT NULL,
            `proposed_instructions` TEXT NOT NULL,
            `proposed_tools` TEXT NOT NULL,
            `proposed_examples` TEXT NOT NULL,
            `rationale` TEXT NOT NULL,
            `memory_facts_json` TEXT NOT NULL,
            `status` TEXT NOT NULL,
            `source` TEXT NOT NULL,
            `created_at` INTEGER NOT NULL,
            `resolved_at` INTEGER
        )
        """.trimIndent(),
        "CREATE INDEX IF NOT EXISTS `index_skill_proposals_status` ON `skill_proposals` (`status`)",
        """
        CREATE TABLE IF NOT EXISTS `skill_settings` (
            `id` INTEGER NOT NULL,
            `auto_accept` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent(),
        "INSERT OR REPLACE INTO `skill_settings` (`id`, `auto_accept`) VALUES (1, 0)"
    )

    val MIGRATION_11_18 = object : Migration(FROM, TO) {
        override fun migrate(db: SupportSQLiteDatabase) {
            STATEMENTS.forEach(db::execSQL)
        }
    }
}
