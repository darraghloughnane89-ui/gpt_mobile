package dev.chungjungsoo.gptmobile.smoke.migration

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2
import dev.chungjungsoo.gptmobile.smoke.SmokeFeature
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrates a v11 database to the current [ChatDatabaseV2] version along the longest
 * available migration path, so a temporary shortcut (for example 11 to 13) does not
 * hide the bot tables added by 11 to 12.
 *
 * When a new migration object is introduced, add its holder class to [MIGRATION_HOLDERS].
 */
@RunWith(AndroidJUnit4::class)
@SmokeFeature("migration")
class RoomMigration11ToLatestTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ChatDatabaseV2::class.java
    )

    @Test
    fun migrate11ToLatest_preservesChatAndMessage() {
        val latest = checkNotNull(ChatDatabaseV2::class.java.getAnnotation(Database::class.java)).version
        helper.createDatabase(DATABASE_NAME, START_VERSION).apply {
            execSQL(
                "INSERT INTO chats_v2 (chat_id, title, enabled_platform, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(CHAT_ID, CHAT_TITLE, "profile-smoke", 1, 2)
            )
            execSQL(
                "INSERT INTO messages_v2 (" +
                    "message_id, chat_id, thoughts, content, attachments, revisions, " +
                    "active_revision_index, linked_message_id, platform_type, current_run_id, created_at, timeline" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(MESSAGE_ID, CHAT_ID, "", MESSAGE_CONTENT, "", "[]", -1, 0, null, null, 3, "[]")
            )
            close()
        }

        val path = longestPath(discoverMigrations(), START_VERSION, latest)
        assertTrue(
            "No migration path from $START_VERSION to $latest. Add the new holder to MIGRATION_HOLDERS.",
            path != null
        )
        val database = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            latest,
            true,
            *checkNotNull(path).toTypedArray()
        )
        database.query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(latest, cursor.getInt(0))
        }
        database.query("SELECT title FROM chats_v2 WHERE chat_id = $CHAT_ID").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(CHAT_TITLE, cursor.getString(0))
        }
        database.query("SELECT content FROM messages_v2 WHERE message_id = $MESSAGE_ID").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(MESSAGE_CONTENT, cursor.getString(0))
        }
        if (database.hasTable("bots")) {
            database.query("SELECT COUNT(*) FROM bots WHERE name = 'Assistant'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue("Built-in Assistant bot was not seeded", cursor.getInt(0) > 0)
            }
        }
        database.close()
    }

    private fun SupportSQLiteDatabase.hasTable(name: String): Boolean {
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name)).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-smoke-11"
        const val START_VERSION = 11
        const val CHAT_ID = 11
        const val MESSAGE_ID = 21
        const val CHAT_TITLE = "Migration smoke"
        const val MESSAGE_CONTENT = "Keep this message"

        val MIGRATION_HOLDERS = listOf(
            "dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2Migrations",
            "dev.chungjungsoo.gptmobile.data.database.migration.BotDatabaseV2Migration",
            "dev.chungjungsoo.gptmobile.data.database.migration.ProjectMigrations"
        )

        fun discoverMigrations(): List<Migration> {
            val found = mutableListOf<Migration>()
            MIGRATION_HOLDERS.forEach { className ->
                val clazz = runCatching { Class.forName(className) }.getOrNull() ?: return@forEach
                val instance = clazz.declaredFields.firstOrNull { it.name == "INSTANCE" }?.let { field ->
                    field.isAccessible = true
                    field.get(null)
                }
                clazz.methods.forEach { method ->
                    if (method.parameterCount != 0) return@forEach
                    if (!Migration::class.java.isAssignableFrom(method.returnType)) return@forEach
                    val staticMethod = Modifier.isStatic(method.modifiers)
                    val receiver = if (staticMethod) null else instance ?: return@forEach
                    val value = runCatching { method.invoke(receiver) }.getOrNull()
                    if (value is Migration) found += value
                }
                clazz.declaredFields.forEach { field ->
                    if (!Migration::class.java.isAssignableFrom(field.type)) return@forEach
                    field.isAccessible = true
                    val receiver = if (Modifier.isStatic(field.modifiers)) null else instance ?: return@forEach
                    val value = runCatching { field.get(receiver) }.getOrNull()
                    if (value is Migration) found += value
                }
            }
            return found.distinctBy { migration -> migration.startVersion to migration.endVersion }
        }

        fun longestPath(migrations: List<Migration>, from: Int, to: Int): List<Migration>? {
            if (from == to) return emptyList()
            val byStart = migrations.groupBy { migration -> migration.startVersion }
            var best: List<Migration>? = null
            fun walk(version: Int, path: List<Migration>, seen: Set<Int>) {
                if (version == to) {
                    if (best == null || path.size > checkNotNull(best).size) best = path
                    return
                }
                byStart[version].orEmpty().forEach { migration ->
                    if (migration.endVersion in seen) return@forEach
                    walk(migration.endVersion, path + migration, seen + migration.endVersion)
                }
            }
            walk(from, emptyList(), setOf(from))
            return best
        }
    }
}
