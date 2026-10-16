package dev.chungjungsoo.gptmobile.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Conversation branches, Room version 23.
 *
 * Versions 14–22 belong to other changes. Installed databases on version 13
 * take this direct additive path. Existing rows keep their text, revisions,
 * and links; the new columns default to a single active sibling.
 */
object ConversationBranchMigrations {
    val BRANCH_COLUMN_SQL = listOf(
        "ALTER TABLE `messages_v2` ADD COLUMN `parent_message_id` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `messages_v2` ADD COLUMN `sibling_index` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `messages_v2` ADD COLUMN `is_active_sibling` INTEGER NOT NULL DEFAULT 1"
    )

    val MIGRATION_13_23 = object : Migration(13, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            BRANCH_COLUMN_SQL.forEach(db::execSQL)
        }
    }
}
