package dev.chungjungsoo.gptmobile.data.memory

import dev.chungjungsoo.gptmobile.data.privacy.PrivacySettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val privacySettings: PrivacySettingsRepository
) {
    fun observeMemories(): Flow<List<Memory>> = memoryDao.observeAll()

    val globallyPaused: Flow<Boolean> = privacySettings.memoryGloballyPaused

    suspend fun saveExplicit(message: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Memory? {
        val fact = ExplicitMemoryParser.extract(message)?.let(MemoryFactPolicy::sanitize) ?: return null
        return upsert(fact, MemorySource.EXPLICIT, nowEpochSeconds)
    }

    suspend fun saveSuggestion(text: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Memory? {
        val fact = MemoryFactPolicy.sanitize(text) ?: return null
        return upsert(fact, MemorySource.SUGGESTION, nowEpochSeconds)
    }

    suspend fun saveFact(
        text: String,
        source: String,
        projectId: Long? = null,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000
    ): Memory? {
        val fact = MemoryFactPolicy.sanitize(text) ?: return null
        return upsert(fact, source, nowEpochSeconds, projectId = projectId)
    }

    suspend fun importMemory(
        text: String,
        source: String,
        projectId: Long?,
        paused: Boolean,
        createdAt: Long,
        updatedAt: Long
    ): Memory? {
        val fact = MemoryFactPolicy.sanitize(text) ?: return null
        return upsert(
            text = fact,
            source = source,
            nowEpochSeconds = updatedAt,
            projectId = projectId,
            paused = paused,
            preservePause = true,
            createdAt = createdAt
        )
    }

    suspend fun updateText(id: Long, text: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000) {
        val clean = MemoryFactPolicy.sanitize(text) ?: return
        val current = memoryDao.getById(id) ?: return
        memoryDao.update(current.copy(text = clean, updatedAt = nowEpochSeconds))
    }

    suspend fun delete(id: Long) {
        memoryDao.deleteById(id)
    }

    suspend fun setPaused(memory: Memory, paused: Boolean, nowEpochSeconds: Long = System.currentTimeMillis() / 1000) {
        memoryDao.update(memory.copy(paused = paused, updatedAt = nowEpochSeconds))
    }

    suspend fun clearAll() {
        memoryDao.deleteAll()
    }

    suspend fun setGloballyPaused(paused: Boolean) {
        privacySettings.setMemoryGloballyPaused(paused)
    }

    suspend fun isEnabledForChat(chatId: Int): Boolean = memoryDao.memoryEnabled(chatId) != false

    suspend fun setEnabledForChat(chatId: Int, enabled: Boolean) {
        if (chatId <= 0) return
        memoryDao.upsertChatSetting(ChatMemorySetting(chatId = chatId, memoryEnabled = enabled))
    }

    private suspend fun upsert(
        text: String,
        source: String,
        nowEpochSeconds: Long,
        projectId: Long? = null,
        paused: Boolean = false,
        preservePause: Boolean = false,
        createdAt: Long? = null
    ): Memory {
        val existing = memoryDao.findByText(text)
        if (existing != null) {
            val updated = existing.copy(
                text = text,
                source = source,
                paused = if (preservePause) paused else false,
                projectId = projectId ?: existing.projectId,
                updatedAt = nowEpochSeconds
            )
            memoryDao.update(updated)
            return updated
        }
        val created = Memory(
            text = text,
            source = source,
            createdAt = createdAt ?: nowEpochSeconds,
            updatedAt = nowEpochSeconds,
            paused = paused,
            projectId = projectId
        )
        val id = memoryDao.insert(created)
        return created.copy(id = id)
    }
}
