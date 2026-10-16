package dev.chungjungsoo.gptmobile.data.database.migration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
