package dev.chungjungsoo.gptmobile.data.portable

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class PortableLibraryFull : Exception()

@Serializable
data class StoredSkill(
    val id: String,
    val name: String,
    val title: String,
    val description: String,
    val instructions: String,
    val tools: List<String> = emptyList(),
    val examples: List<String> = emptyList(),
    val untrusted: Boolean = false,
    val createdAt: Long = 0
) {
    fun toBody(): PortableSkillBody = PortableSkillBody(
        name = name,
        title = title,
        description = description,
        instructions = instructions,
        tools = tools,
        examples = examples
    )
}

@Serializable
data class StoredPrompt(
    val id: String,
    val title: String,
    val text: String,
    val untrusted: Boolean = false,
    val createdAt: Long = 0
) {
    fun toBody(): PortablePromptBody = PortablePromptBody(title = title, text = text)
}

@Serializable
data class PortableLibrarySnapshot(
    val skills: List<StoredSkill> = emptyList(),
    val prompts: List<StoredPrompt> = emptyList(),
    val importedBotUids: List<String> = emptyList()
) {
    fun encode(): String = libraryJson.encodeToString(PortableLibrarySnapshot.serializer(), this)

    companion object {
        fun decode(raw: String): PortableLibrarySnapshot = try {
            libraryJson.decodeFromString(serializer(), raw)
        } catch (_: Exception) {
            PortableLibrarySnapshot()
        }
    }
}

interface PortableLibraryStore {
    suspend fun snapshot(): PortableLibrarySnapshot
    suspend fun upsertSkill(skill: StoredSkill)
    suspend fun upsertPrompt(prompt: StoredPrompt)
    suspend fun deleteSkill(id: String)
    suspend fun deletePrompt(id: String)
    suspend fun rememberImportedBot(botUid: String)
    suspend fun forgetImportedBot(botUid: String)
    suspend fun skill(id: String): StoredSkill?
    suspend fun prompt(id: String): StoredPrompt?
}

class FilePortableLibraryStore @Inject constructor(
    @param:ApplicationContext context: Context
) : PortableLibraryStore {
    private val file = File(File(context.filesDir, "portable"), "library.json")
    private val mutex = Mutex()

    override suspend fun snapshot(): PortableLibrarySnapshot = mutex.withLock { read() }

    override suspend fun upsertSkill(skill: StoredSkill) = update { current ->
        val existing = current.skills.any { it.id == skill.id }
        if (!existing && current.skills.size >= PortableLimits.MAX_LIBRARY_ITEMS) throw PortableLibraryFull()
        val skills = current.skills.filter { it.id != skill.id } + skill
        current.copy(skills = skills.sortedBy { it.title.lowercase() })
    }

    override suspend fun upsertPrompt(prompt: StoredPrompt) = update { current ->
        val existing = current.prompts.any { it.id == prompt.id }
        if (!existing && current.prompts.size >= PortableLimits.MAX_LIBRARY_ITEMS) throw PortableLibraryFull()
        val prompts = current.prompts.filter { it.id != prompt.id } + prompt
        current.copy(prompts = prompts.sortedBy { it.title.lowercase() })
    }

    override suspend fun deleteSkill(id: String) = update { current ->
        current.copy(skills = current.skills.filter { it.id != id })
    }

    override suspend fun deletePrompt(id: String) = update { current ->
        current.copy(prompts = current.prompts.filter { it.id != id })
    }

    override suspend fun rememberImportedBot(botUid: String) = update { current ->
        if (botUid in current.importedBotUids) current else current.copy(importedBotUids = current.importedBotUids + botUid)
    }

    override suspend fun forgetImportedBot(botUid: String) = update { current ->
        current.copy(importedBotUids = current.importedBotUids.filter { it != botUid })
    }

    override suspend fun skill(id: String): StoredSkill? = snapshot().skills.firstOrNull { it.id == id }

    override suspend fun prompt(id: String): StoredPrompt? = snapshot().prompts.firstOrNull { it.id == id }

    private suspend fun update(transform: (PortableLibrarySnapshot) -> PortableLibrarySnapshot) {
        mutex.withLock {
            val next = transform(read())
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "library.json.tmp")
            tmp.writeText(next.encode())
            if (file.exists() && !file.delete()) {
                file.writeText(next.encode())
                tmp.delete()
            } else if (!tmp.renameTo(file)) {
                file.writeText(next.encode())
                tmp.delete()
            }
        }
    }

    private fun read(): PortableLibrarySnapshot {
        if (!file.exists()) return PortableLibrarySnapshot()
        return PortableLibrarySnapshot.decode(file.readText())
    }
}

private val libraryJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}
