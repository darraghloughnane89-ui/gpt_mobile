package dev.chungjungsoo.gptmobile.data.project

import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.Project
import dev.chungjungsoo.gptmobile.data.database.entity.ProjectFile
import dev.chungjungsoo.gptmobile.data.project.archive.ArchiveChatRecord
import dev.chungjungsoo.gptmobile.data.project.archive.ArchiveEntry
import dev.chungjungsoo.gptmobile.data.project.archive.ArchiveFileRecord
import dev.chungjungsoo.gptmobile.data.project.archive.ArchiveProjectRecord
import dev.chungjungsoo.gptmobile.data.project.archive.ArchiveStoredMessage
import dev.chungjungsoo.gptmobile.data.project.archive.ImportSource
import dev.chungjungsoo.gptmobile.data.project.archive.ImportedChat
import dev.chungjungsoo.gptmobile.data.project.archive.ImportedConversation
import dev.chungjungsoo.gptmobile.data.project.archive.ImportedFile
import dev.chungjungsoo.gptmobile.data.project.archive.ImportedMessage
import dev.chungjungsoo.gptmobile.data.project.archive.ImportedProject
import dev.chungjungsoo.gptmobile.data.project.archive.ImportedRole
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveException
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveExporter
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveFailure
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveImporter
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveParser
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveSink
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveZip
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectImportMap
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectTransferStage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectArchiveTest {
    @Test
    fun robinRoundTrip_preservesInstructionsFilesChatsAndStripsTopFolder() {
        val original = robinSample()
        val zipped = ProjectArchiveZip.write(original)
        val entries = ProjectArchiveZip.read(ByteArrayInputStream(zipped)).map { entry ->
            ArchiveEntry("Bundle/${entry.path}", entry.bytes)
        }

        val parsed = ProjectArchiveParser.parse(entries).single()

        assertEquals(ImportSource.ROBIN, parsed.source)
        assertEquals(original.importKey, parsed.importKey)
        assertEquals("Notes", parsed.name)
        assertEquals("work", parsed.icon)
        assertEquals(original.color, parsed.color)
        assertEquals("Be brief", parsed.instructions)
        assertEquals("note.md", parsed.files.single().displayName)
        assertTrue(parsed.files.single().bytes.contentEquals("alpha".toByteArray()))
        assertEquals("ext-1", parsed.chats.single().externalId)
        assertEquals(listOf("Hi", "Hello"), parsed.chats.single().messages.map { it.text })
    }

    @Test
    fun robinMarkdownChat_readsUserAndAssistantSections() {
        val parsed = ProjectArchiveParser.parse(
            listOf(
                ArchiveEntry("instructions.md", "Keep it short".toByteArray()),
                ArchiveEntry(
                    "chats/morning.md",
                    """
                    # Morning

                    ## user
                    Hi

                    ## assistant
                    Hello
                    """.trimIndent().toByteArray()
                )
            )
        ).single()

        assertEquals("Keep it short", parsed.instructions)
        assertEquals("Morning", parsed.chats.single().title)
        assertEquals(listOf(ImportedRole.USER, ImportedRole.ASSISTANT), parsed.chats.single().messages.map { it.role })
    }

    @Test
    fun chatgptMapping_walksCurrentNodeAndHoistsSharedSystemPrompt() {
        val parsed = ProjectArchiveParser.parse(
            listOf(ArchiveEntry("conversations.json", chatgptJson(secondSystem = "Be brief").toByteArray()))
        ).single()

        assertEquals("chatgpt", parsed.importKey)
        assertEquals("ChatGPT", parsed.name)
        assertEquals("Be brief", parsed.instructions)
        assertEquals(2, parsed.chats.size)
        val greeting = parsed.chats.first { it.externalId == "conv-1" }
        assertEquals("Greeting", greeting.title)
        assertEquals(1700000000L, greeting.createdAt)
        assertEquals(listOf(ImportedRole.USER, ImportedRole.ASSISTANT), greeting.messages.map { it.role })
        assertEquals(listOf("Hi", "Hello"), greeting.messages.map { it.text })
        assertFalse(greeting.messages.any { it.text == "tool output" })
        assertFalse(parsed.chats.any { chat -> chat.messages.any { it.text == "Be brief" } })
    }

    @Test
    fun chatgpt_differentSystemPromptsStayOutOfInstructions() {
        val parsed = ProjectArchiveParser.parse(
            listOf(ArchiveEntry("conversations.json", chatgptJson(secondSystem = "Be long").toByteArray()))
        ).single()

        assertEquals("", parsed.instructions)
    }

    @Test
    fun chatgpt_instructionsFileOverridesHoistedPrompt() {
        val parsed = ProjectArchiveParser.parse(
            listOf(
                ArchiveEntry("conversations.json", chatgptJson(secondSystem = "Be brief").toByteArray()),
                ArchiveEntry("instructions.md", "Use the form".toByteArray()),
                ArchiveEntry("project.json", """{"id":"pack-1","name":"Pack"}""".toByteArray())
            )
        ).single()

        assertEquals("chatgpt:pack-1", parsed.importKey)
        assertEquals("Pack", parsed.name)
        assertEquals("Use the form", parsed.instructions)
    }

    @Test
    fun claude_splitsProjectsAndKeepsUnassignedChats() {
        val parsed = ProjectArchiveParser.parse(
            listOf(
                ArchiveEntry("projects.json", claudeProjects().toByteArray()),
                ArchiveEntry("conversations.json", claudeConversations().toByteArray())
            )
        )

        assertEquals(listOf("claude:p1", "claude:p2", "claude"), parsed.map { it.importKey })
        val notes = parsed.first()
        assertEquals("Field notes", notes.name)
        assertEquals("Write clearly", notes.instructions)
        assertEquals("note.md", notes.files.single().displayName)
        assertTrue(notes.files.single().bytes.contentEquals("alpha".toByteArray()))
        assertEquals("Morning", notes.chats.single().title)
        assertEquals(listOf("Hi", "Hello"), notes.chats.single().messages.map { it.text })
        assertEquals(Instant.parse("2024-01-02T03:04:05Z").epochSecond, notes.chats.single().createdAt)
        assertEquals("Loose", parsed.last().chats.single().title)
        assertEquals("Unassigned", parsed.last().chats.single().messages.single().text)
    }

    @Test
    fun grokCustomInstructions_mapsRoleMessages() {
        val parsed = ProjectArchiveParser.parse(
            listOf(ArchiveEntry("export.json", grokMessages().toByteArray()))
        ).single()

        assertEquals(ImportSource.GROK, parsed.source)
        assertEquals("grok", parsed.importKey)
        assertEquals("Be direct", parsed.instructions)
        assertEquals("g1", parsed.chats.single().externalId)
        assertEquals(listOf("Hi", "Hello"), parsed.chats.single().messages.map { it.text })
        assertFalse(parsed.chats.single().messages.any { it.text == "hidden" })
    }

    @Test
    fun grokResponses_mapsUserQueryAndModelResponse() {
        val parsed = ProjectArchiveParser.parse(
            listOf(ArchiveEntry("conversations.json", grokResponses().toByteArray()))
        ).single()

        assertEquals("grok:pack", parsed.importKey)
        assertEquals("Grok", parsed.name)
        assertEquals("Be direct", parsed.instructions)
        assertEquals(listOf("Hi", "Hello"), parsed.chats.single().messages.map { it.text })
    }

    @Test
    fun reimport_updatesTheSameProjectWithoutDuplicatingChatsOrFiles() {
        val sink = MemoryArchiveSink()
        val first = robinSample()
        val created = runBlocking { ProjectArchiveImporter.import(listOf(first), sink) }
        val fileId = sink.files.single().id
        val chatId = sink.chats.single().id
        sink.addLocalChat(sink.projects.single().id)
        sink.insertLocalFile(sink.projects.single().id)

        val second = first.copy(
            instructions = "Updated instructions",
            files = listOf(first.files.single().copy(bytes = "beta".toByteArray())),
            chats = listOf(
                first.chats.single().copy(
                    title = "Renamed",
                    messages = listOf(
                        ImportedMessage(ImportedRole.USER, "Hi again", 5),
                        ImportedMessage(ImportedRole.ASSISTANT, "Hello again", 6)
                    )
                )
            )
        )
        val stages = mutableListOf<ProjectTransferStage>()
        val updated = runBlocking { ProjectArchiveImporter.import(listOf(second), sink) { stages += it.stage } }

        assertEquals(1, created.createdProjects)
        assertEquals(0, created.updatedProjects)
        assertEquals(0, updated.createdProjects)
        assertEquals(1, updated.updatedProjects)
        assertEquals(1, sink.projects.size)
        assertEquals("Updated instructions", sink.projects.single().instructions)
        assertEquals(fileId, sink.files.single { it.displayName == "note.md" }.id)
        assertTrue(sink.files.single { it.displayName == "note.md" }.bytes.contentEquals("beta".toByteArray()))
        assertEquals("beta", sink.files.single { it.displayName == "note.md" }.extractedText)
        assertTrue(sink.files.any { it.displayName == "local.txt" })
        assertEquals(chatId, sink.chats.single { it.title == "Renamed" }.id)
        assertTrue(sink.chats.any { it.title == "Local note" })
        assertEquals(listOf("platform-a", "platform-b"), sink.chats.single { it.id == chatId }.enabledPlatforms)
        val stored = sink.messages.filter { it.chatId == chatId }
        assertEquals(listOf("Hi again", "Hello again"), stored.map { it.text })
        assertNull(stored.first().platformType)
        assertEquals("platform-a", stored.last().platformType)
        assertEquals(stored.first().id, stored.last().linkedMessageId)
        assertTrue(stages.contains(ProjectTransferStage.WRITING))
    }

    @Test
    fun reimport_recreatesChatWhenMappedChatWasDeleted() {
        val sink = MemoryArchiveSink()
        val project = robinSample()
        runBlocking { ProjectArchiveImporter.import(listOf(project), sink) }
        val oldId = sink.chats.single().id
        sink.deleteChat(oldId)

        runBlocking { ProjectArchiveImporter.import(listOf(project), sink) }

        assertEquals(1, sink.projects.size)
        assertEquals(1, sink.chats.size)
        assertNotEquals(oldId, sink.chats.single().id)
        assertEquals(listOf("Hi", "Hello"), sink.messages.map { it.text })
    }

    @Test
    fun export_keepsImportKeyAndExternalIds() {
        val project = Project(
            id = 4,
            name = "Notes",
            icon = "work",
            color = 7,
            instructions = "Be brief",
            importKey = "robin:keep"
        )
        val file = ProjectFile(
            id = 8,
            projectId = 4,
            displayName = "note.md",
            mimeType = "text/markdown",
            storageName = "stored",
            extractedText = "alpha",
            sizeBytes = 5
        )
        val chat = ChatRoomV2(
            id = 3,
            title = "Hello",
            enabledPlatform = listOf("platform-a"),
            projectId = 4,
            createdAt = 10,
            updatedAt = 11
        )
        val messages = listOf(
            MessageV2(id = 1, chatId = 3, content = "Hi", platformType = null, createdAt = 10),
            MessageV2(id = 2, chatId = 3, content = "Hello", platformType = "platform-a", linkedMessageId = 1, createdAt = 11),
            MessageV2(id = 3, chatId = 3, content = "   ", platformType = "platform-b", createdAt = 12)
        )
        val assembled = ProjectArchiveExporter.assemble(
            project = project,
            importKey = "robin:keep",
            files = listOf(file to "alpha".toByteArray()),
            chats = listOf(chat to messages),
            map = ProjectImportMap(chats = mapOf("ext-1" to 3), files = mapOf("files/note.md" to 8))
        )
        val parsed = ProjectArchiveParser.parse(
            ProjectArchiveZip.read(ByteArrayInputStream(ProjectArchiveZip.write(assembled.project)))
        ).single()

        assertEquals("robin:keep", parsed.importKey)
        assertEquals("ext-1", parsed.chats.single().externalId)
        assertEquals(listOf("Hi", "Hello"), parsed.chats.single().messages.map { it.text })
        assertEquals("files/note.md", parsed.files.single().relativePath)
        assertEquals(mapOf("ext-1" to 3), assembled.map.chats)
        assertEquals(mapOf("files/note.md" to 8), assembled.map.files)
    }

    @Test
    fun zipSlip_dropsParentPaths() {
        val raw = ByteArrayOutputStream()
        ZipOutputStream(raw).use { zip ->
            zip.putNextEntry(ZipEntry("../secret.txt"))
            zip.write("nope".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("instructions.md"))
            zip.write("hello".toByteArray())
            zip.closeEntry()
        }

        val entries = ProjectArchiveZip.read(ByteArrayInputStream(raw.toByteArray()))

        assertEquals(listOf("instructions.md"), entries.map { it.path })
    }

    @Test
    fun unrecognizedArchive_failsWithoutCreatingAProject() {
        val error = assertThrows(ProjectArchiveException::class.java) {
            ProjectArchiveParser.parse(listOf(ArchiveEntry("readme.txt", "hello".toByteArray())))
        }

        assertEquals(ProjectArchiveFailure.UNRECOGNIZED, error.failure)
    }

    private fun robinSample() = ImportedProject(
        importKey = "robin:keep",
        name = "Notes",
        instructions = "Be brief",
        icon = "work",
        color = 0xFF006A6A.toInt(),
        source = ImportSource.ROBIN,
        files = listOf(ImportedFile("files/note.md", "note.md", "alpha".toByteArray(), "text/markdown")),
        chats = listOf(
            ImportedChat(
                externalId = "ext-1",
                title = "Hello",
                createdAt = 10,
                updatedAt = 11,
                messages = listOf(
                    ImportedMessage(ImportedRole.USER, "Hi", 10),
                    ImportedMessage(ImportedRole.ASSISTANT, "Hello", 11)
                )
            )
        )
    )

    private fun chatgptJson(secondSystem: String) = """
        [
          {
            "title": "Greeting",
            "id": "conv-1",
            "create_time": 1700000000.5,
            "update_time": 1700000100,
            "current_node": "n3",
            "mapping": {
              "n1": {"id": "n1", "message": {"author": {"role": "system"}, "content": {"parts": ["Be brief"]}, "create_time": 1700000000}, "parent": null, "children": ["n2"]},
              "n2": {"id": "n2", "message": {"author": {"role": "user"}, "content": {"parts": ["Hi"]}, "create_time": 1700000001}, "parent": "n1", "children": ["tool", "n3"]},
              "tool": {"id": "tool", "message": {"author": {"role": "tool"}, "content": {"parts": ["tool output"]}}, "parent": "n2", "children": []},
              "n3": {"id": "n3", "message": {"author": {"role": "assistant"}, "content": {"parts": ["Hello"]}, "create_time": 1700000002}, "parent": "n2", "children": []}
            }
          },
          {
            "title": "Second",
            "id": "conv-2",
            "create_time": 1700000200,
            "current_node": "b2",
            "mapping": {
              "b1": {"id": "b1", "message": {"author": {"role": "system"}, "content": {"parts": ["$secondSystem"]}}, "parent": null, "children": ["b2"]},
              "b2": {"id": "b2", "message": {"author": {"role": "user"}, "content": {"parts": ["Status"]}}, "parent": "b1", "children": []}
            }
          }
        ]
    """.trimIndent()

    private fun claudeProjects() = """
        [
          {"uuid": "p1", "name": "Field notes", "prompt_template": "Write clearly", "docs": [{"filename": "note.md", "content": "alpha"}]},
          {"uuid": "p2", "name": "Other", "description": "Secondary"}
        ]
    """.trimIndent()

    private fun claudeConversations() = """
        [
          {
            "uuid": "c1",
            "name": "Morning",
            "project_uuid": "p1",
            "created_at": "2024-01-02T03:04:05Z",
            "chat_messages": [
              {"sender": "human", "text": "Hi", "created_at": "2024-01-02T03:04:05Z"},
              {"sender": "assistant", "content": [{"type": "text", "text": "Hello"}], "created_at": "2024-01-02T03:05:00Z"}
            ]
          },
          {
            "uuid": "c2",
            "name": "Loose",
            "created_at": "2024-01-03T00:00:00Z",
            "chat_messages": [
              {"sender": "human", "text": "Unassigned"}
            ]
          }
        ]
    """.trimIndent()

    private fun grokMessages() = """
        {
          "customInstructions": "Be direct",
          "conversations": [
            {
              "conversationId": "g1",
              "title": "Hello",
              "createdAt": "2024-01-02T03:04:05Z",
              "messages": [
                {"role": "system", "content": "hidden"},
                {"role": "user", "content": "Hi"},
                {"role": "assistant", "content": "Hello"}
              ]
            }
          ]
        }
    """.trimIndent()

    private fun grokResponses() = """
        {
          "id": "pack",
          "conversations": [
            {
              "conversation": {"id": "g2", "title": "Hello", "system_prompt": "Be direct"},
              "responses": [
                {"response": {"user_query": "Hi", "model_response": "Hello"}}
              ]
            }
          ]
        }
    """.trimIndent()
}

private class MemoryArchiveSink(
    private val enabled: List<String> = listOf("platform-a", "platform-b")
) : ProjectArchiveSink {
    val projects = mutableListOf<ArchiveProjectRecord>()
    val chats = mutableListOf<ArchiveChatRecord>()
    val files = mutableListOf<ArchiveFileRecord>()
    val messages = mutableListOf<ArchiveStoredMessage>()
    private val maps = mutableMapOf<Int, ProjectImportMap>()
    private var nextProject = 1
    private var nextChat = 1
    private var nextFile = 1
    private var nextMessage = 1

    fun addLocalChat(projectId: Int) {
        chats += ArchiveChatRecord(
            id = nextChat++,
            projectId = projectId,
            title = "Local note",
            enabledPlatforms = enabled,
            createdAt = 1,
            updatedAt = 1
        )
    }

    fun insertLocalFile(projectId: Int) {
        files += ArchiveFileRecord(
            id = nextFile++,
            projectId = projectId,
            displayName = "local.txt",
            mimeType = "text/plain",
            bytes = "mine".toByteArray(),
            extractedText = "mine"
        )
    }

    fun deleteChat(chatId: Int) {
        chats.removeAll { it.id == chatId }
        messages.removeAll { it.chatId == chatId }
    }

    override suspend fun findByImportKey(importKey: String): ArchiveProjectRecord? =
        projects.firstOrNull { it.importKey == importKey }

    override suspend fun insertProject(
        name: String,
        icon: String,
        color: Int,
        instructions: String,
        importKey: String,
        createdAt: Long,
        updatedAt: Long
    ): ArchiveProjectRecord {
        val record = ArchiveProjectRecord(nextProject++, name, icon, color, instructions, importKey, createdAt, updatedAt)
        projects += record
        return record
    }

    override suspend fun updateProject(record: ArchiveProjectRecord) {
        val index = projects.indexOfFirst { it.id == record.id }
        if (index >= 0) projects[index] = record
    }

    override suspend fun readMap(projectId: Int): ProjectImportMap = maps[projectId] ?: ProjectImportMap()

    override suspend fun writeMap(projectId: Int, map: ProjectImportMap) {
        maps[projectId] = map
    }

    override suspend fun getChat(chatId: Int): ArchiveChatRecord? = chats.firstOrNull { it.id == chatId }

    override suspend fun insertChat(
        projectId: Int,
        title: String,
        enabledPlatforms: List<String>,
        createdAt: Long,
        updatedAt: Long
    ): ArchiveChatRecord {
        val record = ArchiveChatRecord(nextChat++, projectId, title, enabledPlatforms, createdAt, updatedAt)
        chats += record
        return record
    }

    override suspend fun updateChat(record: ArchiveChatRecord) {
        val index = chats.indexOfFirst { it.id == record.id }
        if (index >= 0) chats[index] = record
    }

    override suspend fun replaceMessages(chatId: Int, messages: List<ImportedMessage>, assistantPlatformUid: String) {
        ImportedConversation.persist(
            messages = messages,
            assistantPlatformUid = assistantPlatformUid,
            clear = { this.messages.removeAll { it.chatId == chatId } },
            insert = { text, createdAt, platformType, linkedMessageId ->
                val id = nextMessage++
                this.messages += ArchiveStoredMessage(id, chatId, text, createdAt, platformType, linkedMessageId)
                id
            }
        )
    }

    override suspend fun getFile(fileId: Int): ArchiveFileRecord? = files.firstOrNull { it.id == fileId }

    override suspend fun insertFile(
        projectId: Int,
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
        extractedText: String?
    ): ArchiveFileRecord {
        val record = ArchiveFileRecord(nextFile++, projectId, displayName, mimeType, bytes, extractedText)
        files += record
        return record
    }

    override suspend fun replaceFile(
        record: ArchiveFileRecord,
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
        extractedText: String?
    ) {
        val index = files.indexOfFirst { it.id == record.id }
        if (index >= 0) {
            files[index] = record.copy(
                displayName = displayName,
                mimeType = mimeType,
                bytes = bytes,
                extractedText = extractedText
            )
        }
    }

    override suspend fun enabledPlatformUids(): List<String> = enabled
}
