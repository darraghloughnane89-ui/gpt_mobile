package dev.chungjungsoo.gptmobile.data.agent

import dev.chungjungsoo.gptmobile.data.bot.BotRepository
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@Singleton
class BackgroundReplyStarter @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingRepository: SettingRepository,
    private val botRepository: BotRepository,
    private val agentRunCoordinator: AgentRunCoordinator
) {
    suspend fun reply(chatId: Int, text: String): Boolean {
        if (text.isBlank() || chatId <= 0) return false
        if (agentRunCoordinator.hasActiveRuns(chatId)) return false
        val chat = chatRepository.fetchChatListV2().firstOrNull { it.id == chatId } ?: return false
        val plan = planBackgroundReply(
            chat = chat,
            history = chatRepository.fetchMessagesV2(chatId),
            platforms = settingRepository.fetchPlatformV2s(),
            chatPlatformModels = chatRepository.fetchChatPlatformModels(chatId),
            bot = chat.botUid?.let { botRepository.getBot(it) },
            replyText = text,
            nowSeconds = System.currentTimeMillis() / 1000
        ) ?: return false
        return withContext(NonCancellable) {
            val persisted = chatRepository.persistAgentTurn(plan.persist)
            val requests = requestsForPersistedReply(plan, persisted)
            if (requests.isEmpty()) return@withContext false
            agentRunCoordinator.start(requests)
            true
        }
    }
}
