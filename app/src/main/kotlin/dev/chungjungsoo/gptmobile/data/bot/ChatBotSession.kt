package dev.chungjungsoo.gptmobile.data.bot

import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.data.instruction.ChatCompletionContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatBotSession @Inject constructor(
    private val botRepository: BotRepository
) {
    private val _bots = MutableStateFlow<List<Bot>>(emptyList())
    val bots = _bots.asStateFlow()

    private val _selectedBot = MutableStateFlow<Bot?>(null)
    val selectedBot = _selectedBot.asStateFlow()

    suspend fun prepareNewChat(): String? {
        val bot = botRepository.lastSelectedBotUid()?.let { botRepository.getBot(it) }
        _selectedBot.value = bot
        _bots.value = botRepository.listBots()
        return bot?.botUid
    }

    suspend fun bind(botUid: String?) {
        val bot = botUid?.let { botRepository.getBot(it) }
        _selectedBot.value = bot
        _bots.value = botRepository.listBots()
    }

    suspend fun refresh() {
        _bots.value = botRepository.listBots()
        val selectedUid = _selectedBot.value?.botUid
        _selectedBot.value = selectedUid?.let { uid -> _bots.value.firstOrNull { it.botUid == uid } }
            ?: selectedUid?.let { botRepository.getBot(it) }
    }

    suspend fun select(botUid: String?): Bot? {
        val bot = botUid?.let { botRepository.getBot(it) }
        _selectedBot.value = bot
        botRepository.setLastSelectedBotUid(bot?.botUid)
        _bots.value = botRepository.listBots()
        return bot
    }

    suspend fun assignChat(chatId: Int, botUid: String?) {
        botRepository.assignChatBot(chatId, botUid)
    }

    fun completionContext(chatId: Int): ChatCompletionContext {
        val bot = _selectedBot.value
        return ChatCompletionContext(
            chatId = chatId,
            botUid = bot?.botUid,
            botInstructions = bot?.instructions
        )
    }

    fun newChatModelOverride(enabledPlatformUids: Collection<String>): Pair<String, String>? =
        _selectedBot.value?.modelOverride(enabledPlatformUids)

    suspend fun selectedBotNeedsLocalNetwork(): Boolean {
        val botUid = _selectedBot.value?.botUid ?: return false
        return botRepository.needsLocalNetwork(botUid)
    }
}
