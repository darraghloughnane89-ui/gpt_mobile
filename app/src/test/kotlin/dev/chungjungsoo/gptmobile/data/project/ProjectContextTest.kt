package dev.chungjungsoo.gptmobile.data.project

import dev.chungjungsoo.gptmobile.data.context.ContextBuilder
import dev.chungjungsoo.gptmobile.data.context.InstructionStack
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.Project
import dev.chungjungsoo.gptmobile.data.database.entity.ProjectFile
import dev.chungjungsoo.gptmobile.data.database.migration.ProjectMigrations
import dev.chungjungsoo.gptmobile.data.model.ClientType
import java.io.File
import java.util.zip.Deflater
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectContextTest {
    private val composer = ProjectContextComposer()

    @Test
    fun compose_layers_globalThenProjectThenBotThenChat() {
        val stack = InstructionStack.compose(
            global = "Platform prompt",
            project = "Project notes",
            bot = "Bot voice",
            chat = "Chat note"
        )

        assertEquals(
            "Platform prompt\n\nProject notes\n\nBot voice\n\nChat note",
            stack.composed()
        )
    }

    @Test
    fun compose_blankLayers_areSkipped() {
        val stack = InstructionStack.compose(global = "Platform prompt", bot = "   ", chat = "Chat note")

        assertEquals("Platform prompt\n\nChat note", stack.composed())
    }

    @Test
    fun applyProjectLayer_blankLayer_keepsPlatformPrompt() {
        val platform = samplePlatform(systemPrompt = "Stay concise")

        val updated = ContextBuilder().applyProjectLayer(platform, "  ")

        assertEquals("Stay concise", updated.systemPrompt)
    }

    @Test
    fun applyProjectLayer_projectText_followsGlobalPrompt() {
        val platform = samplePlatform(systemPrompt = "Stay concise")

        val updated = ContextBuilder().applyProjectLayer(platform, "Use the project glossary.")

        assertEquals("Stay concise\n\nUse the project glossary.", updated.systemPrompt)
    }

    @Test
    fun compose_instructionsAndFiles_respectSizeLimits() {
        val notes = "a".repeat(ProjectContextLimits.MAX_INSTRUCTION_CHARS + 50)
        val files = listOf(
            file("notes.md", "text/markdown", "m".repeat(ProjectContextLimits.MAX_FILE_CHARS + 20)),
            file("photo.png", "image/png", null, sizeBytes = 2048)
        )

        val text = composer.compose(notes, files)

        assertFalse(text.contains("a".repeat(ProjectContextLimits.MAX_INSTRUCTION_CHARS + 1)))
        assertTrue(text.contains("### notes.md"))
        assertTrue(text.contains("### photo.png"))
        assertTrue(text.contains("not inlined"))
        assertTrue(text.length <= ProjectContextLimits.MAX_CONTEXT_CHARS + "\n\n".length)
    }

    @Test
    fun compose_overflow_omitsLaterFiles() {
        val files = (1..8).map { index ->
            file("part-$index.md", "text/markdown", "x".repeat(ProjectContextLimits.MAX_FILE_CHARS))
        }

        val text = composer.compose("", files)

        assertTrue(text.contains("omitted"))
        assertFalse(text.contains("### part-8.md"))
    }

    @Test
    fun classify_extension_whenMimeIsGeneric() {
        assertEquals(ProjectFileKind.PDF, ProjectFileTypes.classify("application/octet-stream", "paper.PDF"))
        assertEquals(ProjectFileKind.TEXT, ProjectFileTypes.classify(null, "notes.md"))
        assertEquals(ProjectFileKind.IMAGE, ProjectFileTypes.classify("image/jpeg", "shot.jpg"))
        assertNull(ProjectFileTypes.classify("application/zip", "bundle.zip"))
    }

    @Test
    fun extract_literalPdfText() {
        val pdf = """
            %PDF-1.4
            4 0 obj
            << /Length 44 >>
            stream
            BT (Hello project) Tj ET
            endstream
            endobj
            %%EOF
        """.trimIndent().toByteArray()

        assertEquals("Hello project", PdfTextExtractor.extract(pdf, 100))
    }

    @Test
    fun extract_flateDecodedPdfText_andHonorsMaxChars() {
        val content = "BT (Secret note) Tj ET"
        val pdf = pdfWithFlateStream(content)

        assertEquals("Secret note", PdfTextExtractor.extract(pdf, 100))
        assertEquals("Sec", PdfTextExtractor.extract(pdf, 3))
    }

    @Test
    fun groupChatsByProject_allFilter_groupsKnownProjectsAndLeavesTheRest() {
        val projects = listOf(
            Project(id = 2, name = "Beta", icon = "folder", color = 1),
            Project(id = 1, name = "Alpha", icon = "folder", color = 1)
        )
        val chats = listOf(
            chat(1, null),
            chat(2, 1),
            chat(3, 2),
            chat(4, 9)
        )

        val rows = groupChatsByProject(chats, projects, ProjectFilter.All, "No project")

        assertEquals(
            listOf("project-2", "chat-3", "project-1", "chat-2", "none", "chat-1", "chat-4"),
            rows.map { row ->
                when (row) {
                    is ChatListRow.Header -> row.key
                    is ChatListRow.Chat -> "chat-${row.chat.id}"
                }
            }
        )
        val alphaChat = rows.filterIsInstance<ChatListRow.Chat>().first { it.chat.id == 2 }
        assertEquals(1, alphaChat.chatIndex)
    }

    @Test
    fun groupChatsByProject_projectFilter_keepsOriginalSelectionIndex() {
        val chats = listOf(chat(1, null), chat(2, 7))

        val rows = groupChatsByProject(chats, emptyList(), ProjectFilter.Project(7), "No project")

        assertEquals(1, rows.filterIsInstance<ChatListRow.Chat>().single().chatIndex)
    }

    @Test
    fun migration12To13_andTemporary11To13_shareProjectSql() {
        assertEquals(12, ProjectMigrations.MIGRATION_12_13.startVersion)
        assertEquals(13, ProjectMigrations.MIGRATION_12_13.endVersion)
        assertEquals(11, ProjectMigrations.MIGRATION_11_13.startVersion)
        assertEquals(13, ProjectMigrations.MIGRATION_11_13.endVersion)
        val sql = ProjectMigrations.PROJECT_SCHEMA_SQL.joinToString("\n")
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `projects`"))
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `project_files`"))
        assertTrue(sql.contains("ALTER TABLE `chats_v2` ADD COLUMN `project_id` INTEGER"))
        assertTrue(sql.contains("index_chats_v2_project_id"))
        assertFalse(sql.contains("bot_uid"))
    }

    @Test
    fun exportedSchema13_includesProjectTablesAndNullableChatProjectId() {
        val schema = schemaFile().readText()
        val root = Json.parseToJsonElement(schema).jsonObject
        val database = root.getValue("database").jsonObject
        assertEquals("13", database.getValue("version").jsonPrimitive.content)
        val tables = database.getValue("entities").jsonArray.associate { entity ->
            val obj = entity.jsonObject
            obj.getValue("tableName").jsonPrimitive.content to obj
        }
        assertTrue("projects" in tables)
        assertTrue("project_files" in tables)
        val projectId = tables.getValue("chats_v2").getValue("fields").jsonArray.map { it.jsonObject }
            .first { it.getValue("columnName").jsonPrimitive.content == "project_id" }
        assertEquals("INTEGER", projectId.getValue("affinity").jsonPrimitive.content)
        assertFalse(projectId.containsKey("notNull") && projectId.getValue("notNull").jsonPrimitive.content == "true")
    }

    private fun schemaFile(): File {
        val relative = "schemas/dev.chungjungsoo.gptmobile.data.database.ChatDatabaseV2/13.json"
        return listOf(File(relative), File("app/$relative")).firstOrNull { it.exists() }
            ?: error("Room schema 13 was not exported")
    }

    private fun samplePlatform(systemPrompt: String) = PlatformV2(
        name = "OpenAI",
        compatibleType = ClientType.OPENAI,
        apiUrl = "https://api.openai.com/v1/",
        model = "gpt-test",
        systemPrompt = systemPrompt
    )

    private fun file(name: String, mime: String, text: String?, sizeBytes: Long = text?.length?.toLong() ?: 0) = ProjectFile(
        projectId = 1,
        displayName = name,
        mimeType = mime,
        storageName = name,
        extractedText = text,
        sizeBytes = sizeBytes
    )

    private fun chat(id: Int, projectId: Int?) = dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2(
        id = id,
        title = "Chat $id",
        enabledPlatform = listOf("profile"),
        projectId = projectId
    )

    private fun pdfWithFlateStream(content: String): ByteArray {
        val deflater = Deflater()
        deflater.setInput(content.toByteArray())
        deflater.finish()
        val compressed = ByteArray(256)
        val length = deflater.deflate(compressed)
        deflater.end()
        val stream = compressed.copyOf(length)
        val header = "%PDF-1.4\n4 0 obj\n<< /Filter /FlateDecode /Length $length >>\nstream\n"
        val footer = "\nendstream\nendobj\n%%EOF"
        return header.toByteArray() + stream + footer.toByteArray()
    }
}
