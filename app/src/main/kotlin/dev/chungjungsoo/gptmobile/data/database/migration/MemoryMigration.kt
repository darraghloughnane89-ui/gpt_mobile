package dev.chungjungsoo.gptmobile.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 15. Creates long-term memory tables without changing chats, messages, or platforms.
 * Versions 12–14 belong to other streams. This migration starts at the current main version, 11.
 */
object MemoryMigration {
    val CREATE_MEMORIES = """
        CREATE TABLE IF NOT EXISTS `memories` (
            `memory_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `text` TEXT NOT NULL,
            `source` TEXT NOT NULL,
            `created_at` INTEGER NOT NULL,
            `updated_at` INTEGER NOT NULL,
            `paused` INTEGER NOT NULL,
            `project_id` INTEGER
        )
    """.trimIndent()

    val CREATE_CHAT_MEMORY_SETTINGS = """
        CREATE TABLE IF NOT EXISTS `chat_memory_settings` (
            `chat_id` INTEGER NOT NULL,
            `memory_enabled` INTEGER NOT NULL,
            PRIMARY KEY(`chat_id`)
        )
    """.trimIndent()

    val MIGRATION_11_15 = object : Migration(11, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(CREATE_MEMORIES)
            db.execSQL(CREATE_CHAT_MEMORY_SETTINGS)
        }
    }
}
