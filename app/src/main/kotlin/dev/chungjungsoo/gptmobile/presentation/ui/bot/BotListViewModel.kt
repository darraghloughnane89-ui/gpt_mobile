package dev.chungjungsoo.gptmobile.presentation.ui.bot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.bot.BotRepository
import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.presentation.fastentry.FastEntryShortcutPublisher
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BotListViewModel @Inject constructor(
    private val botRepository: BotRepository,
    private val shortcuts: FastEntryShortcutPublisher
) : ViewModel() {
    private val _bots = MutableStateFlow<List<Bot>>(emptyList())
    val bots = _bots.asStateFlow()

    private val _favouriteUids = MutableStateFlow<Set<String>>(emptySet())
    val favouriteUids = _favouriteUids.asStateFlow()

    private val _canRestoreBuiltIns = MutableStateFlow(false)
    val canRestoreBuiltIns = _canRestoreBuiltIns.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _bots.value = botRepository.listBots()
            _favouriteUids.value = botRepository.favouriteBotUids()
            _canRestoreBuiltIns.value = botRepository.missingBuiltIns().isNotEmpty()
        }
    }

    fun toggleFavourite(botUid: String) {
        viewModelScope.launch {
            val favourite = botUid !in botRepository.favouriteBotUids()
            botRepository.setBotFavourite(botUid, favourite)
            refresh()
            shortcuts.publishAsync()
        }
    }

    fun duplicate(botUid: String) {
        viewModelScope.launch {
            botRepository.duplicate(botUid)
            refresh()
        }
    }

    fun delete(botUid: String) {
        viewModelScope.launch {
            botRepository.delete(botUid)
            refresh()
            shortcuts.publishAsync()
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
}
