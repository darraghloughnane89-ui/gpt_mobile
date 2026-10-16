package dev.chungjungsoo.gptmobile.data.skill

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SkillReviewUnavailable(message: String) : Exception(message)

/**
 * Asks the first enabled platform to review a transcript.
 * Remote profiles use a synthetic uid so assigned tools are not invoked.
 */
@Singleton
class PlatformSkillReviewer @Inject constructor(
    private val settingRepository: SettingRepository,
    private val chatRepository: ChatRepository
) : SkillReviewer {
    override suspend fun review(system: String, user: String): String = withContext(Dispatchers.IO) {
        val platform = settingRepository.fetchPlatformV2s().firstOrNull { it.enabled }
            ?: throw SkillReviewUnavailable("Add an enabled platform before saving a skill from a chat.")
        val reviewPlatform = if (platform.compatibleType == ClientType.LITERT_LM) {
            platform.copy(systemPrompt = system)
        } else {
            platform.copy(uid = REVIEW_UID, systemPrompt = system)
        }
        val chunks = StringBuilder()
        var failure: String? = null
        chatRepository.completeChat(
            userMessages = listOf(MessageV2(chatId = 0, content = user, platformType = null)),
            assistantMessages = listOf(emptyList()),
            platform = reviewPlatform,
            runId = UUID.randomUUID().toString()
        ).collect { state ->
            when (state) {
                is ApiState.Success -> chunks.append(state.textChunk)
                is ApiState.Error -> if (failure == null) failure = state.message
                else -> Unit
            }
        }
        val text = chunks.toString().trim()
        if (text.isEmpty()) {
            throw SkillReviewUnavailable(failure ?: "The platform returned an empty skill review.")
        }
        text
    }

    private companion object {
        const val REVIEW_UID = "skill-review"
    }
}
