package dev.chungjungsoo.gptmobile.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.chungjungsoo.gptmobile.data.database.migration.DriveMigration
import dev.chungjungsoo.gptmobile.data.database.migration.MemoryMigration
import dev.chungjungsoo.gptmobile.data.database.migration.UsageMigration
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ChatDatabaseV2::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate11To16_keepsChatAndAddsMemoryAndUsage() {
        helper.createDatabase(TEST_DATABASE, 11).apply {
            execSQL(
                "INSERT INTO chats_v2 (chat_id, title, enabled_platform, created_at, updated_at) " +
                    "VALUES (4, 'Kept chat', 'profile-1', 10, 11)"
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            16,
            true,
            MemoryMigration.MIGRATION_11_15,
            UsageMigration.MIGRATION_15_16
        )

        database.query("SELECT title FROM chats_v2 WHERE chat_id = 4").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Kept chat", cursor.getString(0))
        }
        database.execSQL(
            "INSERT INTO memories (text, source, created_at, updated_at, paused, project_id) " +
                "VALUES ('I like tea', 'explicit', 1, 1, 0, NULL)"
        )
        database.query("SELECT text, project_id FROM memories").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("I like tea", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        database.query("SELECT COUNT(*) FROM model_prices").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getInt(0) > 0)
        }
        database.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate16To17_addsDriveSyncStateAndKeepsChats() {
        helper.createDatabase(DRIVE_DATABASE, 16).apply {
            execSQL(
                "INSERT INTO chats_v2 (chat_id, title, enabled_platform, created_at, updated_at) " +
                    "VALUES (4, 'Kept chat', 'profile-1', 10, 11)"
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            DRIVE_DATABASE,
            17,
            true,
            DriveMigration.MIGRATION_16_17
        )

        database.query("SELECT title FROM chats_v2 WHERE chat_id = 4").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Kept chat", cursor.getString(0))
        }
        database.execSQL(
            "INSERT INTO drive_sync_state (sync_id, drive_file_id, last_backup_at, last_restore_at, content_hash) " +
                "VALUES (1, 'file-1', 20, NULL, 'abc')"
        )
        database.query("SELECT drive_file_id FROM drive_sync_state WHERE sync_id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("file-1", cursor.getString(0))
        }
        database.close()
    }

    private companion object {
        const val TEST_DATABASE = "privacy-migration-test"
        const val DRIVE_DATABASE = "drive-migration-test"
    }
}
