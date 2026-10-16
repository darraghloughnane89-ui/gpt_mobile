package dev.chungjungsoo.gptmobile.data.database.migration

import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillSchemaMigrationTest {
    @Test
    fun statements_createTheSkillTablesOnSqlite() {
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE chats_v2 (
                    chat_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    enabled_platform TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            statement.execute("INSERT INTO chats_v2 (chat_id, title, enabled_platform, created_at, updated_at) VALUES (4, 'Kept', 'p', 1, 2)")
            SkillSchemaMigration.STATEMENTS.forEach { sql -> statement.execute(sql) }
        }

        val tables = mutableListOf<String>()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { cursor ->
                while (cursor.next()) tables += cursor.getString(1)
            }
        }
        assertTrue(tables.contains("skills"))
        assertTrue(tables.contains("bot_skill_links"))
        assertTrue(tables.contains("skill_proposals"))
        assertTrue(tables.contains("skill_settings"))

        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT title FROM chats_v2 WHERE chat_id = 4").use { cursor ->
                assertTrue(cursor.next())
                assertEquals("Kept", cursor.getString(1))
            }
            statement.executeQuery("SELECT auto_accept FROM skill_settings WHERE id = 1").use { cursor ->
                assertTrue(cursor.next())
                assertEquals(0, cursor.getInt(1))
            }
        }
        assertEquals(11, SkillSchemaMigration.FROM)
        assertEquals(18, SkillSchemaMigration.TO)
        connection.close()
    }
}
