package dev.chungjungsoo.gptmobile.data.database

import dev.chungjungsoo.gptmobile.data.database.migration.ConversationBranchMigrations
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationBranchMigrationTest {
    @Test
    fun `version 23 migration is additive from version 13`() {
        assertEquals(13, ConversationBranchMigrations.MIGRATION_13_23.startVersion)
        assertEquals(23, ConversationBranchMigrations.MIGRATION_13_23.endVersion)
        assertEquals(
            listOf(
                "ALTER TABLE `messages_v2` ADD COLUMN `parent_message_id` INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE `messages_v2` ADD COLUMN `sibling_index` INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE `messages_v2` ADD COLUMN `is_active_sibling` INTEGER NOT NULL DEFAULT 1"
            ),
            ConversationBranchMigrations.BRANCH_COLUMN_SQL
        )
    }

    @Test
    fun `existing chats migrate intact`() {
        val database = File.createTempFile("conversation-branch", ".db")
        database.deleteOnExit()
        sqlite(
            database,
            """
            CREATE TABLE messages_v2 (
                message_id INTEGER PRIMARY KEY NOT NULL,
                chat_id INTEGER NOT NULL,
                thoughts TEXT NOT NULL,
                content TEXT NOT NULL,
                attachments TEXT NOT NULL,
                revisions TEXT NOT NULL,
                active_revision_index INTEGER NOT NULL,
                linked_message_id INTEGER NOT NULL,
                platform_type TEXT,
                current_run_id TEXT,
                created_at INTEGER NOT NULL,
                timeline TEXT NOT NULL DEFAULT '[]'
            );
            INSERT INTO messages_v2 (
                message_id, chat_id, thoughts, content, attachments, revisions,
                active_revision_index, linked_message_id, platform_type, current_run_id,
                created_at, timeline
            ) VALUES (
                11, 7, 'kept thought', 'Old question', '[]',
                '[{"content":"Old answer","thoughts":"","createdAt":1234}]',
                -1, 0, NULL, 'run-old', 102, '[]'
            );
            INSERT INTO messages_v2 (
                message_id, chat_id, thoughts, content, attachments, revisions,
                active_revision_index, linked_message_id, platform_type, current_run_id,
                created_at, timeline
            ) VALUES (
                12, 7, '', 'Old answer', '[]', '[]', -1, 11, 'profile-1', 'run-old', 103, '[]'
            );
            """.trimIndent()
        )
        ConversationBranchMigrations.BRANCH_COLUMN_SQL.forEach { statement ->
            sqlite(database, statement)
        }

        val rows = sqlite(
            database,
            """
            SELECT message_id, content, thoughts, revisions, active_revision_index,
                   linked_message_id, platform_type, current_run_id,
                   parent_message_id, sibling_index, is_active_sibling
            FROM messages_v2
            ORDER BY message_id;
            """.trimIndent()
        )

        assertEquals(
            """
            11|Old question|kept thought|[{"content":"Old answer","thoughts":"","createdAt":1234}]|-1|0||run-old|0|0|1
            12|Old answer||[]|-1|11|profile-1|run-old|0|0|1
            """.trimIndent(),
            rows
        )
        assertTrue(rows.contains("Old question"))
        assertTrue(rows.contains("Old answer"))
    }

    private fun sqlite(database: File, sql: String): String {
        val process = ProcessBuilder("sqlite3", "-batch", database.absolutePath)
            .redirectErrorStream(true)
            .start()
        process.outputStream.bufferedWriter().use { writer ->
            writer.write(sql)
            writer.newLine()
        }
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        check(exitCode == 0) { output }
        return output
    }
}
