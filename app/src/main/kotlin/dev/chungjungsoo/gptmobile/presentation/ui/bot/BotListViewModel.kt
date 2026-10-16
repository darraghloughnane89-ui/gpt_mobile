package dev.chungjungsoo.gptmobile.presentation.ui.bot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.bot.BotDraft
import dev.chungjungsoo.gptmobile.data.bot.BotRepository
import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.data.portable.PortableDocuments
import dev.chungjungsoo.gptmobile.data.portable.PortableLibraryStore
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BotListViewModel @Inject constructor(
    private val botRepository: BotRepository,
    private val library: PortableLibraryStore
) : ViewModel() {
    private val _bots = MutableStateFlow<List<Bot>>(emptyList())
    val bots = _bots.asStateFlow()

    private val _canRestoreBuiltIns = MutableStateFlow(false)
    val canRestoreBuiltIns = _canRestoreBuiltIns.asStateFlow()

    private val _importedBotUids = MutableStateFlow<Set<String>>(emptySet())
    val importedBotUids = _importedBotUids.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _bots.value = botRepository.listBots()
            _canRestoreBuiltIns.value = botRepository.missingBuiltIns().isNotEmpty()
            _importedBotUids.value = library.snapshot().importedBotUids.toSet()
        }
    }

    fun duplicate(botUid: String) {
        viewModelScope.launch {
            val copy = botRepository.duplicate(botUid)
            if (copy != null && botUid in _importedBotUids.value) {
                library.rememberImportedBot(copy.botUid)
            }
            refresh()
        }
    }

    fun delete(botUid: String) {
        viewModelScope.launch {
            botRepository.delete(botUid)
            library.forgetImportedBot(botUid)
            refresh()
        }
    }

    fun reset(botUid: String) {
        viewModelScope.launch {
            botRepository.resetBuiltIn(botUid)
            refresh()
        }
    }

    fun restoreDeletedBuiltIns() {
        viewModelScope.launch {
            botRepository.restoreMissingBuiltIns()
            refresh()
        }
    }

    suspend fun exportJson(botUid: String): String? {
        val bot = botRepository.getBot(botUid) ?: return null
        val draft = BotDraft.fromBot(bot, botRepository.listBindings(botUid))
        return PortableDocuments.exportBot(draft)
    }
}
