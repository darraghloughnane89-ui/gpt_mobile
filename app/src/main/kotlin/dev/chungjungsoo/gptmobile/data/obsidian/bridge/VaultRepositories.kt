package dev.chungjungsoo.gptmobile.data.obsidian.bridge

data class MemoryNote(
    val id: String,
    val title: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val projectName: String? = null,
    val updatedEpochSeconds: Long
)

data class SkillNote(
    val id: String,
    val name: String,
    val description: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val updatedEpochSeconds: Long
)

data class ProjectNote(
    val id: String,
    val name: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val updatedEpochSeconds: Long
)

data class BotNote(
    val id: String,
    val name: String,
    val instructions: String,
    val tags: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val updatedEpochSeconds: Long
)

data class VaultChat(
    val id: String,
    val title: String,
    val projectName: String,
    val createdAtEpochSeconds: Long,
    val updatedAtEpochSeconds: Long,
    val tags: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val messages: List<dev.chungjungsoo.gptmobile.data.obsidian.VaultMessage> = emptyList()
)

interface MemoryRepository {
    suspend fun listMemories(): List<MemoryNote>
    suspend fun upsertMemory(note: MemoryNote)
    suspend fun deleteMemory(id: String)
}

interface SkillRepository {
    suspend fun listSkills(): List<SkillNote>
    suspend fun upsertSkill(note: SkillNote)
    suspend fun deleteSkill(id: String)
}

interface ProjectRepository {
    suspend fun listProjects(): List<ProjectNote>
    suspend fun upsertProject(note: ProjectNote)
    suspend fun deleteProject(id: String)
    suspend fun projectNameForChat(chatId: String): String?
}

interface BotRepository {
    suspend fun listBots(): List<BotNote>
    suspend fun upsertBot(note: BotNote)
    suspend fun deleteBot(id: String)
}

interface ChatVaultSource {
    suspend fun listChats(): List<VaultChat>
    suspend fun roomExists(roomId: Int): Boolean
    suspend fun createChat(title: String, updatedEpochSeconds: Long): Int
    suspend fun appendMessage(roomId: Int, message: dev.chungjungsoo.gptmobile.data.obsidian.VaultMessage): Int
    suspend fun updateMessage(roomId: Int, roomMessageId: Int, message: dev.chungjungsoo.gptmobile.data.obsidian.VaultMessage)
    suspend fun updateChat(roomId: Int, title: String, updatedEpochSeconds: Long)
    suspend fun messageIds(roomId: Int): Set<Int>
}

class StubMemoryRepository : MemoryRepository {
    override suspend fun listMemories(): List<MemoryNote> = emptyList()
    override suspend fun upsertMemory(note: MemoryNote) = Unit
    override suspend fun deleteMemory(id: String) = Unit
}

class StubSkillRepository : SkillRepository {
    override suspend fun listSkills(): List<SkillNote> = emptyList()
    override suspend fun upsertSkill(note: SkillNote) = Unit
    override suspend fun deleteSkill(id: String) = Unit
}

class StubProjectRepository : ProjectRepository {
    override suspend fun listProjects(): List<ProjectNote> = emptyList()
    override suspend fun upsertProject(note: ProjectNote) = Unit
    override suspend fun deleteProject(id: String) = Unit
    override suspend fun projectNameForChat(chatId: String): String? = null
}

class StubBotRepository : BotRepository {
    override suspend fun listBots(): List<BotNote> = emptyList()
    override suspend fun upsertBot(note: BotNote) = Unit
    override suspend fun deleteBot(id: String) = Unit
}
