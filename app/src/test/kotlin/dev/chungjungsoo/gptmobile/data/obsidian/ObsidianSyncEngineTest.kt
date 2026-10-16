package dev.chungjungsoo.gptmobile.data.obsidian

import dev.chungjungsoo.gptmobile.data.obsidian.bridge.ChatVaultSource
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.MemoryNote
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.MemoryRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.StubBotRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.StubProjectRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.StubSkillRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.VaultChat
import dev.chungjungsoo.gptmobile.util.Brand
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ObsidianSyncEngineTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun sync_remoteMemory_pullsIntoRepository() = runBlocking {
        val remote = VaultMarkdown.render(
            VaultDocument(
                id = "mem-tea",
                type = VaultNoteType.MEMORY,
                updatedEpochSeconds = 30,
                title = "Tea",
                body = "Likes tea",
                tags = listOf("robin/memory")
            )
        )
        val vault = MemoryVault(
            mutableListOf(VaultRemoteFile("Robin/Memory/Tea.md", remote, "sha"))
        )
        val memories = FakeMemories()
        val engine = engine(vault, memories, FakeChats())

        val report = engine.sync()

        assertTrue(report.ok)
        assertEquals("Likes tea", memories.notes.getValue("mem-tea").body)
        assertEquals(1, report.pulled)
    }

    @Test
    fun sync_localChat_pushesMarkdownUnderProjectFolder() = runBlocking {
        val vault = MemoryVault(mutableListOf())
        val chats = FakeChats()
        chats.chats += VaultChat(
            id = "chat-7",
            title = "Hello",
            projectName = "Inbox",
            createdAtEpochSeconds = 1_792_000_000,
            updatedAtEpochSeconds = 1_792_000_100,
            messages = listOf(VaultMessage("m1", "user", "Hello", 1_792_000_000))
        )
        val engine = engine(vault, FakeMemories(), chats)

        engine.sync()

        val written = vault.files.single()
        assertTrue(written.path.startsWith("${Brand.VAULT_ROOT}/Chats/Inbox/"))
        assertTrue(written.path.endsWith(" Hello.md"))
        assertEquals("chat-7", VaultMarkdown.parse(written.content)?.id)
    }

    @Test
    fun missingConfig_defaultsToKnowledgeVault() = runBlocking {
        val config = FileObsidianSyncPreferences(File(folder.root, "missing.json")).load()

        assertEquals(VaultBackend.GITHUB.storageKey, config.backend)
        assertEquals(Brand.VAULT_GITHUB_OWNER, config.githubOwner)
        assertEquals(Brand.VAULT_GITHUB_REPO, config.githubRepo)
        assertEquals(Brand.VAULT_GITHUB_BRANCH, config.githubBranch)
        assertEquals(Brand.VAULT_ROOT, config.rootFolder)
    }

    private fun engine(vault: MemoryVault, memories: MemoryRepository, chats: ChatVaultSource): ObsidianSyncEngine {
        val prefs = FileObsidianSyncPreferences(folder.newFile("config.json"))
        val index = FileObsidianSyncIndexStore(folder.newFile("index.json"))
        return ObsidianSyncEngine(
            preferences = prefs,
            indexStore = index,
            vaults = VaultSyncFactory { vault },
            chats = chats,
            memories = memories,
            skills = StubSkillRepository(),
            projects = StubProjectRepository(),
            bots = StubBotRepository()
        )
    }
}

private class MemoryVault(
    val files: MutableList<VaultRemoteFile>
) : VaultSync {
    override suspend fun readTree(): VaultReadResult = VaultReadResult.Ok(files.toList())

    override suspend fun commit(change: VaultCommit): VaultCommitResult {
        change.files.forEach { file ->
            files.removeAll { it.path == file.path }
            files += VaultRemoteFile(file.path, file.content, file.content.hashCode().toString())
        }
        return VaultCommitResult.Ok("committed")
    }
}

private class FakeMemories : MemoryRepository {
    val notes = linkedMapOf<String, MemoryNote>()

    override suspend fun listMemories(): List<MemoryNote> = notes.values.toList()

    override suspend fun upsertMemory(note: MemoryNote) {
        notes[note.id] = note
    }

    override suspend fun deleteMemory(id: String) {
        notes.remove(id)
    }
}

private class FakeChats : ChatVaultSource {
    val chats = mutableListOf<VaultChat>()

    override suspend fun listChats(): List<VaultChat> = chats.toList()

    override suspend fun roomExists(roomId: Int): Boolean = chats.any { it.id == "chat-$roomId" }

    override suspend fun createChat(title: String, updatedEpochSeconds: Long): Int = error("not used")

    override suspend fun appendMessage(roomId: Int, message: VaultMessage): Int = 1

    override suspend fun updateMessage(roomId: Int, roomMessageId: Int, message: VaultMessage) = Unit

    override suspend fun updateChat(roomId: Int, title: String, updatedEpochSeconds: Long) = Unit

    override suspend fun messageIds(roomId: Int): Set<Int> = chats
        .filter { it.id == "chat-$roomId" }
        .flatMap { chat -> chat.messages }
        .mapNotNull { message -> message.id.removePrefix("m").toIntOrNull() }
        .toSet()
}
