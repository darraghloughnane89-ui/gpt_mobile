package dev.chungjungsoo.gptmobile.data.memory

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.privacy.PrivacySettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

interface MemoryPromptSource {
    suspend fun promptBlock(chatId: Int, latestUserText: String): String?

    object None : MemoryPromptSource {
        override suspend fun promptBlock(chatId: Int, latestUserText: String): String? = null
    }
}

@Singleton
class RoomMemoryPromptSource @Inject constructor(
    private val memoryDao: MemoryDao,
    private val privacySettings: PrivacySettingsRepository
) : MemoryPromptSource {
    override suspend fun promptBlock(chatId: Int, latestUserText: String): String? {
        if (privacySettings.memoryGloballyPaused.first()) return null
        if (chatId > 0 && memoryDao.memoryEnabled(chatId) == false) return null
        val facts = memoryDao.getActive()
            .filter { it.projectId == null }
            .map { it.text }
        return MemoryPromptComposer.compose(facts, latestUserText)
    }
}

fun PlatformV2.includingMemory(block: String?): PlatformV2 {
    if (block.isNullOrBlank()) return this
    val existing = systemPrompt?.trim().orEmpty()
    val combined = if (existing.isEmpty()) block else "$existing\n\n$block"
    return copy(systemPrompt = combined)
}
