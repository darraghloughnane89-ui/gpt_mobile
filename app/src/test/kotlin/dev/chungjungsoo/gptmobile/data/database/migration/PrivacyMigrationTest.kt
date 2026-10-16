package dev.chungjungsoo.gptmobile.data.database.migration

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyMigrationTest {
    @Test
    fun memoryMigration_createsOnlyMemoryTables() {
        val sql = MemoryMigration.CREATE_MEMORIES + MemoryMigration.CREATE_CHAT_MEMORY_SETTINGS
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `memories`"))
        assertTrue(sql.contains("`project_id` INTEGER"))
        assertFalse(sql.contains("chats_v2"))
        assertFalse(sql.contains("messages_v2"))
        assertFalse(sql.contains("platform_v2"))
        assertEquals(11, MemoryMigration.MIGRATION_11_15.startVersion)
        assertEquals(15, MemoryMigration.MIGRATION_11_15.endVersion)
    }

    @Test
    fun usageMigration_createsUsageTablesAndSeedsPrices() {
        assertTrue(UsageMigration.CREATE_USAGE_EVENTS.contains("CREATE TABLE IF NOT EXISTS `usage_events`"))
        assertTrue(UsageMigration.CREATE_MODEL_PRICES.contains("PRIMARY KEY(`model_key`)"))
        assertTrue(UsageMigration.MIGRATION_15_16.startVersion == 15)
        assertTrue(UsageMigration.MIGRATION_15_16.endVersion == 16)
        assertTrue(dev.chungjungsoo.gptmobile.data.usage.ModelPriceCatalog.insertStatements().any { it.contains("gpt-4o") })
    }

    @Test
    fun exportedSchema16_matchesMigrationCreateStatements() {
        val schema = File("schemas/dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2/16.json")
        assertTrue("Room schema 16 was not exported", schema.exists())
        val entities = Json.parseToJsonElement(schema.readText())
            .jsonObject.getValue("database")
            .jsonObject.getValue("entities")
            .jsonArray
        assertEquals(
            normalize(MemoryMigration.CREATE_MEMORIES),
            createSql(entities, "memories")
        )
        assertEquals(
            normalize(MemoryMigration.CREATE_CHAT_MEMORY_SETTINGS),
            createSql(entities, "chat_memory_settings")
        )
        assertEquals(
            normalize(UsageMigration.CREATE_USAGE_EVENTS),
            createSql(entities, "usage_events")
        )
        assertEquals(
            normalize(UsageMigration.CREATE_MODEL_PRICES),
            createSql(entities, "model_prices")
        )
    }

    @Test
    fun exportedSchema17_addsOnlyDriveSyncState() {
        val schema16 = entities(16)
        val schema17 = entities(17)
        assertEquals(17, schemaVersion(17))
        assertEquals(16, DriveMigration.MIGRATION_16_17.startVersion)
        assertEquals(17, DriveMigration.MIGRATION_16_17.endVersion)
        assertEquals(tableNames(schema16) + "drive_sync_state", tableNames(schema17))
        assertEquals(
            normalize(DriveMigration.CREATE_DRIVE_SYNC_STATE),
            createSql(schema17, "drive_sync_state")
        )
        assertEquals(createSql(schema16, "chats_v2"), createSql(schema17, "chats_v2"))
        assertEquals(createSql(schema16, "messages_v2"), createSql(schema17, "messages_v2"))
        assertEquals(createSql(schema16, "platform_v2"), createSql(schema17, "platform_v2"))
        assertFalse(DriveMigration.CREATE_DRIVE_SYNC_STATE.contains("chats_v2"))
        assertFalse(DriveMigration.CREATE_DRIVE_SYNC_STATE.contains("messages_v2"))
        assertFalse(DriveMigration.CREATE_DRIVE_SYNC_STATE.contains("platform_v2"))
    }

    private fun entities(version: Int): kotlinx.serialization.json.JsonArray {
        val schema = File("schemas/dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2/$version.json")
        assertTrue("Room schema $version was not exported", schema.exists())
        return Json.parseToJsonElement(schema.readText())
            .jsonObject.getValue("database")
            .jsonObject.getValue("entities")
            .jsonArray
    }

    private fun schemaVersion(version: Int): Int {
        val schema = File("schemas/dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2/$version.json")
        return Json.parseToJsonElement(schema.readText())
            .jsonObject.getValue("database")
            .jsonObject.getValue("version")
            .jsonPrimitive.int
    }

    private fun tableNames(entities: kotlinx.serialization.json.JsonArray): Set<String> = entities.map { it.jsonObject.getValue("tableName").jsonPrimitive.content }.toSet()

    private fun createSql(entities: kotlinx.serialization.json.JsonArray, table: String): String {
        val raw = entities.map { it.jsonObject }
            .first { it.getValue("tableName").jsonPrimitive.content == table }
            .getValue("createSql")
            .jsonPrimitive
            .content
        return normalize(raw.replace("\${TABLE_NAME}", table))
    }

    private fun normalize(sql: String) = sql
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s*([()\\[\\],])\\s*"), "$1")
        .trim()
}
