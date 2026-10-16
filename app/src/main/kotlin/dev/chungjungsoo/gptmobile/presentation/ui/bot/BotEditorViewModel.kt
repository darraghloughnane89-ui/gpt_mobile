package dev.chungjungsoo.gptmobile.presentation.ui.bot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.bot.BotDraft
import dev.chungjungsoo.gptmobile.data.bot.BotRepository
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnectionType
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.repository.ToolConnectionRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BotEditorUiState(
    val draft: BotDraft = BotDraft(),
    val platforms: List<PlatformV2> = emptyList(),
    val searchConnections: List<ToolConnection> = emptyList(),
    val mcpConnections: List<ToolConnection> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class BotEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val botRepository: BotRepository,
    private val settingRepository: SettingRepository,
    private val toolConnectionRepository: ToolConnectionRepository
) : ViewModel() {
    private val editingUid: String? = savedStateHandle.get<String>("botUid")
    private val _uiState = MutableStateFlow(BotEditorUiState())
    val uiState = _uiState.asStateFlow()
    val isEditing: Boolean = editingUid != null

    init {
        viewModelScope.launch { load() }
    }

    fun updateDraft(transform: (BotDraft) -> BotDraft) {
        _uiState.update { it.copy(draft = transform(it.draft), errorMessage = null) }
    }

    fun save(onSaved: () -> Unit) {
        val draft = _uiState.value.draft.sanitized()
        if (draft.name.isBlank()) {
            _uiState.update { it.copy(draft = draft, errorMessage = "Enter a name.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val now = System.currentTimeMillis() / 1000
                val uid = draft.botUid ?: UUID.randomUUID().toString()
                val createdAt = if (draft.botUid == null) {
                    now
                } else {
                    botRepository.getBot(uid)?.createdAt ?: now
                }
                val bot = draft.toBot(uid = uid, now = now, createdAt = createdAt)
                botRepository.save(bot, draft.toBindings(uid, now))
                _uiState.update { it.copy(isSaving = false, draft = draft.copy(botUid = uid)) }
                onSaved()
            } catch (error: IllegalArgumentException) {
                _uiState.update { it.copy(isSaving = false, errorMessage = error.message) }
            }
        }
    }

    fun duplicate(onDuplicated: () -> Unit) {
        val uid = _uiState.value.draft.botUid ?: return
        viewModelScope.launch {
            botRepository.duplicate(uid)
            onDuplicated()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val uid = _uiState.value.draft.botUid ?: return
        viewModelScope.launch {
            botRepository.delete(uid)
            onDeleted()
        }
    }

    fun resetBuiltIn() {
        val uid = _uiState.value.draft.botUid ?: return
        viewModelScope.launch {
            if (botRepository.resetBuiltIn(uid) != null) {
                load()
            }
        }
    }

    private suspend fun load() {
        val platforms = settingRepository.fetchPlatformV2s()
        val connections = toolConnectionRepository.listConnections()
        val bot = editingUid?.let { botRepository.getBot(it) }
        val bindings = bot?.let { botRepository.listBindings(it.botUid) }.orEmpty()
        val draft = if (bot == null) {
            BotDraft()
        } else {
            BotDraft.fromBot(bot, bindings)
        }
        _uiState.value = BotEditorUiState(
            draft = draft,
            platforms = platforms,
            searchConnections = connections.filter { it.type != ToolConnectionType.MCP },
            mcpConnections = connections.filter { it.type == ToolConnectionType.MCP },
            isLoading = false,
            errorMessage = if (editingUid != null && bot == null) "This bot was deleted." else null
        )
    }
}
