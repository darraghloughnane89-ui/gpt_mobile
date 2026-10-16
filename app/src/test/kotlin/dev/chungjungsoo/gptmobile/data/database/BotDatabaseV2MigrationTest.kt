package dev.chungjungsoo.gptmobile.data.database

import dev.chungjungsoo.gptmobile.data.database.migration.BotDatabaseV2Migration
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotDatabaseV2MigrationTest {
    @Test
    fun exportedSchema_isVersion12_withBotTablesAndChatBotUid() {
        val database = schema().jsonObject["database"]!!.jsonObject
        assertEquals("12", database["version"]!!.jsonPrimitive.content)
        val entities = database["entities"]!!.jsonArray.map { it.jsonObject }
        val tableNames = entities.map { it["tableName"]!!.jsonPrimitive.content }
        assertTrue(tableNames.contains("bots"))
        assertTrue(tableNames.contains("bot_tool_bindings"))

        val chats = entities.first { it["tableName"]!!.jsonPrimitive.content == "chats_v2" }
        val chatColumns = chats["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }
        assertTrue(chatColumns.contains("bot_uid"))
        assertEquals(
            BotDatabaseV2Migration.CREATE_BOTS_SQL,
            chatsCreate(entities, "bots")
        )
        assertEquals(
            BotDatabaseV2Migration.CREATE_BOT_TOOL_BINDINGS_SQL,
            chatsCreate(entities, "bot_tool_bindings")
        )
        assertTrue(BotDatabaseV2Migration.CREATE_CHAT_BOT_INDEX_SQL.contains("index_chats_v2_bot_uid"))
        assertEquals(11, BotDatabaseV2Migration.MIGRATION_11_12.startVersion)
        assertEquals(12, BotDatabaseV2Migration.MIGRATION_11_12.endVersion)
    }

    private fun chatsCreate(entities: List<kotlinx.serialization.json.JsonObject>, tableName: String): String {
        val createSql = entities.first { it["tableName"]!!.jsonPrimitive.content == tableName }["createSql"]!!.jsonPrimitive.content
        return createSql.replace("\${TABLE_NAME}", tableName)
    }

    private fun schema() = Json.parseToJsonElement(schemaFile().readText())

    private fun schemaFile(): File {
        val candidates = listOf(
            File("schemas/dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2/12.json"),
            File("app/schemas/dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2/12.json")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Missing Room schema 12. Looked in ${candidates.joinToString { it.path }}")
    }
}
