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
        val fact = ExplicitMemoryParser.extract(message) ?: return null
        return upsert(fact, MemorySource.EXPLICIT, nowEpochSeconds)
    }

    suspend fun saveSuggestion(text: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Memory? {
        val fact = text.trim()
        if (fact.isEmpty()) return null
        return upsert(fact, MemorySource.SUGGESTION, nowEpochSeconds)
    }

    suspend fun updateText(id: Long, text: String, nowEpochSeconds: Long = System.currentTimeMillis() / 1000) {
        val clean = text.trim()
        if (clean.isEmpty()) return
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

    private suspend fun upsert(text: String, source: String, nowEpochSeconds: Long): Memory {
        val existing = memoryDao.findByText(text)
        if (existing != null) {
            val updated = existing.copy(
                text = text,
                source = source,
                paused = false,
                updatedAt = nowEpochSeconds
            )
            memoryDao.update(updated)
            return updated
        }
        val created = Memory(
            text = text,
            source = source,
            createdAt = nowEpochSeconds,
            updatedAt = nowEpochSeconds
        )
        val id = memoryDao.insert(created)
        return created.copy(id = id)
    }
}
