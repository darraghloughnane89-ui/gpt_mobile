package dev.chungjungsoo.gptmobile.data.project

import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.migration.BotDatabaseV2Migration
import dev.chungjungsoo.gptmobile.data.database.migration.ProjectMigrations
import dev.chungjungsoo.gptmobile.data.instruction.InstructionStack
import dev.chungjungsoo.gptmobile.data.instruction.InstructionStackComposer
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectInstructionWiringTest {
    @Test
    fun projectInstructions_sitBetweenPlatformAndBot() = runBlocking {
        val source = source(
            chats = mapOf(4 to chat(id = 4, projectId = 9)),
            layers = mapOf(9 to "Use the glossary")
        )
        val stack = InstructionStackComposer(source).forChat(
            chatId = 4,
            global = "Platform prompt",
            bot = "Be a coder",
            chat = "This chat only"
        )

        assertEquals(
            """
            Platform prompt

            Project instructions:
            Use the glossary

            Bot instructions:
            Be a coder

            Chat instructions:
            This chat only
            """.trimIndent(),
            stack.compose()
        )
        assertEquals(
            listOf("Platform prompt", "Use the glossary", "Be a coder", "This chat only"),
            listOf(stack.global, stack.project, stack.bot, stack.chat)
        )
    }

    @Test
    fun chatWithoutProject_leavesPlatformPromptByteIdentical() = runBlocking {
        val prompt = "  Be brief.  "
        val source = source(chats = mapOf(4 to chat(id = 4, projectId = null)))
        val stack = InstructionStackComposer(source).forChat(
            chatId = 4,
            global = prompt,
            bot = null,
            chat = null
        )

        assertNull(stack.project)
        assertEquals(prompt, stack.compose())
        assertEquals(prompt, InstructionStack(global = prompt).compose())
    }

    @Test
    fun unsavedChat_doesNotInventProjectInstructions() = runBlocking {
        val source = source(
            chats = mapOf(4 to chat(id = 4, projectId = 9)),
            layers = mapOf(9 to "Use the glossary")
        )

        assertNull(source.instructionsFor(0))
        assertNull(source.instructionsFor(-1))
    }

    @Test
    fun blankProjectLayer_isSkipped() = runBlocking {
        val source = source(
            chats = mapOf(4 to chat(id = 4, projectId = 9)),
            layers = mapOf(9 to "   ")
        )

        assertNull(source.instructionsFor(4))
    }

    @Test
    fun roomVersions_chainElevenToTwelveToThirteen() {
        assertEquals(11, BotDatabaseV2Migration.MIGRATION_11_12.startVersion)
        assertEquals(12, BotDatabaseV2Migration.MIGRATION_11_12.endVersion)
        assertEquals(12, ProjectMigrations.MIGRATION_12_13.startVersion)
        assertEquals(13, ProjectMigrations.MIGRATION_12_13.endVersion)
        assertEquals(
            BotDatabaseV2Migration.MIGRATION_11_12.endVersion,
            ProjectMigrations.MIGRATION_12_13.startVersion
        )

        val version12 = schema(12)
        val version13 = schema(13)
        assertFalse(columns(version12, "chats_v2").contains("project_id"))
        assertTrue(columns(version12, "chats_v2").contains("bot_uid"))
        assertTrue(tableNames(version12).contains("bots"))
        assertFalse(tableNames(version12).contains("projects"))

        assertTrue(columns(version13, "chats_v2").contains("bot_uid"))
        assertTrue(columns(version13, "chats_v2").contains("project_id"))
        assertTrue(tableNames(version13).containsAll(listOf("bots", "bot_tool_bindings", "projects", "project_files")))
    }

    private fun source(
        chats: Map<Int, ChatRoomV2>,
        layers: Map<Int, String> = emptyMap()
    ) = RoomProjectInstructionSource(
        chatRoomDao = object : ChatRoomV2Dao {
            override suspend fun getChatRooms(): List<ChatRoomV2> = chats.values.toList()

            override suspend fun getChatRoom(chatId: Int): ChatRoomV2? = chats[chatId]

            override suspend fun searchChatRoomsByTitle(query: String): List<ChatRoomV2> = emptyList()

            override suspend fun addChatRoom(chatRoom: ChatRoomV2): Long = chatRoom.id.toLong()

            override suspend fun editChatRoom(chatRoom: ChatRoomV2) = Unit

            override suspend fun deleteChatRooms(vararg chatRooms: ChatRoomV2) = Unit
        },
        projectContextProvider = ProjectContextProvider { projectId ->
            projectId?.let { layers[it] }.orEmpty()
        }
    )

    private fun chat(id: Int, projectId: Int?) = ChatRoomV2(
        id = id,
        title = "Chat $id",
        enabledPlatform = listOf("platform"),
        projectId = projectId
    )

    private fun schema(version: Int) = Json.parseToJsonElement(schemaFile(version).readText())
        .jsonObject.getValue("database").jsonObject

    private fun tableNames(database: kotlinx.serialization.json.JsonObject): Set<String> =
        database.getValue("entities").jsonArray.map { entity ->
            entity.jsonObject.getValue("tableName").jsonPrimitive.content
        }.toSet()

    private fun columns(database: kotlinx.serialization.json.JsonObject, table: String): Set<String> {
        val entity = database.getValue("entities").jsonArray.map { it.jsonObject }
            .first { it.getValue("tableName").jsonPrimitive.content == table }
        return entity.getValue("fields").jsonArray.map { field ->
            field.jsonObject.getValue("columnName").jsonPrimitive.content
        }.toSet()
    }

    private fun schemaFile(version: Int): File {
        val relative = "schemas/dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2/$version.json"
        return listOf(File(relative), File("app/$relative")).firstOrNull { it.exists() }
            ?: error("Room schema $version was not exported")
    }
}
