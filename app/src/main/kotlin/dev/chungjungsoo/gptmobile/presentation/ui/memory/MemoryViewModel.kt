package dev.chungjungsoo.gptmobile.presentation.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.memory.Memory
import dev.chungjungsoo.gptmobile.data.memory.MemoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {
    val memories: StateFlow<List<Memory>> = memoryRepository.observeMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val globallyPaused: StateFlow<Boolean> = memoryRepository.globallyPaused
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setGloballyPaused(paused: Boolean) {
        viewModelScope.launch { memoryRepository.setGloballyPaused(paused) }
    }

    fun setPaused(memory: Memory, paused: Boolean) {
        viewModelScope.launch { memoryRepository.setPaused(memory, paused) }
    }

    fun updateText(id: Long, text: String) {
        viewModelScope.launch { memoryRepository.updateText(id, text) }
    }

    fun addMemory(text: String) {
        viewModelScope.launch { memoryRepository.saveSuggestion(text) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { memoryRepository.delete(id) }
    }

    fun clearAll() {
        viewModelScope.launch { memoryRepository.clearAll() }
    }
}
