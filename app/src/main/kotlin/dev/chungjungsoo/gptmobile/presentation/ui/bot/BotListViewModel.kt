package dev.chungjungsoo.gptmobile.presentation.ui.bot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.bot.BotRepository
import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class BotListViewModel @Inject constructor(
    private val botRepository: BotRepository
) : ViewModel() {
    private val _bots = MutableStateFlow<List<Bot>>(emptyList())
    val bots = _bots.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _bots.value = botRepository.listBots()
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
        }
    }
}
