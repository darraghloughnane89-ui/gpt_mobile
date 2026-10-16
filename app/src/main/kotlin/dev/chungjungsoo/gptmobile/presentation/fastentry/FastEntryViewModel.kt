package dev.chungjungsoo.gptmobile.presentation.fastentry

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.bot.BotRepository
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject

@HiltViewModel
class FastEntryViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val botRepository: BotRepository,
    private val chatRepository: ChatRepository,
    private val shortcuts: FastEntryShortcutPublisher
) : ViewModel() {
    suspend fun enabledPlatformUids(): List<String> = settingRepository.fetchPlatformV2s()
        .filter { it.enabled }
        .map { it.uid }

    suspend fun rememberBot(botUid: String?) {
        if (botUid.isNullOrBlank()) return
        if (botRepository.getBot(botUid) == null) return
        botRepository.setLastSelectedBotUid(botUid)
    }

    suspend fun chatExists(chatId: Int): Boolean = chatId > 0 &&
        chatRepository.fetchChatListV2().any { it.id == chatId }

    fun refreshShortcuts() {
        shortcuts.publishAsync()
    }
}
