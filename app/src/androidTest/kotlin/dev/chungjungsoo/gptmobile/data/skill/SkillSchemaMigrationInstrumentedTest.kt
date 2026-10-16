package dev.chungjungsoo.gptmobile.data.skill

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2
import dev.chungjungsoo.gptmobile.data.database.migration.SkillSchemaMigration
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SkillSchemaMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ChatDatabaseV2::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate11To18_keepsChatsAndAddsSkillTables() {
        helper.createDatabase(TEST_DATABASE, 11).apply {
            execSQL(
                "INSERT INTO chats_v2 (chat_id, title, enabled_platform, created_at, updated_at) " +
                    "VALUES (4, 'Kept chat', 'profile-1', 10, 11)"
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            18,
            true,
            SkillSchemaMigration.MIGRATION_11_18
        )

        database.query("SELECT title FROM chats_v2 WHERE chat_id = 4").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Kept chat", cursor.getString(0))
        }
        database.execSQL(
            """
            INSERT INTO skills (
                stable_name, title, description, instructions, tool_requirements, example_inputs,
                enabled, pinned, origin, use_count, thumbs_up, thumbs_down, last_used_at, created_at, updated_at
            ) VALUES (
                'deploy-staging', 'Deploy', 'Deploy staging', 'Check health', '[]', '[]',
                1, 0, 'user', 0, 0, 0, NULL, 1, 1
            )
            """.trimIndent()
        )
        database.query("SELECT stable_name FROM skills").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("deploy-staging", cursor.getString(0))
        }
        database.query("SELECT auto_accept FROM skill_settings WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        database.close()
    }

    private companion object {
        const val TEST_DATABASE = "skill-migration-11-18"
    }
}
