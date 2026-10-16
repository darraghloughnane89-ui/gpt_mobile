package dev.chungjungsoo.gptmobile.presentation.ui.home

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.agent.AgentRunCoordinator
import dev.chungjungsoo.gptmobile.data.bot.NewChatBotHint
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.Project
import dev.chungjungsoo.gptmobile.data.project.ProjectChatListSource
import dev.chungjungsoo.gptmobile.data.project.ProjectFilter
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val settingRepository: SettingRepository,
    private val agentRunCoordinator: AgentRunCoordinator,
    private val projectChatListSource: ProjectChatListSource,
    private val newChatBotHint: NewChatBotHint = NewChatBotHint.None,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    data class ChatListState(
        val chats: List<ChatRoomV2> = listOf(),
        val isLoading: Boolean = true,
        val loadError: String? = null,
        val isSelectionMode: Boolean = false,
        val isSearchMode: Boolean = false,
        val selectedPlatforms: List<Boolean> = listOf(),
        val selectedChats: List<Boolean> = listOf()
    )

    private val restoredSearch = restoreHomeSearch(
        savedStateHandle.get<String>(HOME_SEARCH_QUERY_KEY),
        savedStateHandle.get<Boolean>(HOME_SEARCH_MODE_KEY)
    )

    private val _chatListState = MutableStateFlow(ChatListState(isSearchMode = restoredSearch.searchMode))
    val chatListState: StateFlow<ChatListState> = _chatListState.asStateFlow()

    private val _platformState = MutableStateFlow(listOf<PlatformV2>())
    val platformState = _platformState.asStateFlow()

    private val _searchQuery = MutableStateFlow(restoredSearch.query)
    val searchQuery = _searchQuery.asStateFlow()

    private val _showSelectModelDialog = MutableStateFlow(savedStateHandle.get<Boolean>(HOME_SELECT_MODEL_KEY) == true)
    val showSelectModelDialog: StateFlow<Boolean> = _showSelectModelDialog.asStateFlow()

    private val _showDeleteWarningDialog = MutableStateFlow(savedStateHandle.get<Boolean>(HOME_DELETE_WARNING_KEY) == true)
    val showDeleteWarningDialog: StateFlow<Boolean> = _showDeleteWarningDialog.asStateFlow()

    private val _activeChatIds = MutableStateFlow<Set<Int>>(emptySet())
    val activeChatIds = _activeChatIds.asStateFlow()

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects = _projects.asStateFlow()

    private val _projectFilter = MutableStateFlow<ProjectFilter>(ProjectFilter.All)
    val projectFilter = _projectFilter.asStateFlow()

    private val _showMoveDialog = MutableStateFlow(false)
    val showMoveDialog = _showMoveDialog.asStateFlow()

    private var fetchChatsJob: Job? = null

    init {
        if (restoredSearch.searchMode) {
            searchChats(restoredSearch.query)
        }
        // Set up debounced search
        _searchQuery
            .drop(1)
            .debounce(SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            .onEach { query -> searchChats(query) }
            .launchIn(viewModelScope)
        agentRunCoordinator.activeRuns
            .onEach { runs -> _activeChatIds.update { runs.values.mapTo(mutableSetOf()) { it.chatId } } }
            .launchIn(viewModelScope)
    }

    fun updatePlatformCheckedState(idx: Int) {
        if (idx < 0 || idx >= _chatListState.value.selectedPlatforms.size) return

        _chatListState.update {
            it.copy(
                selectedPlatforms = it.selectedPlatforms.mapIndexed { index, b ->
                    if (index == idx) {
                        !b
                    } else {
                        b
                    }
                }
            )
        }
    }

    fun updateSearchQuery(query: String) {
        savedStateHandle[HOME_SEARCH_QUERY_KEY] = query
        _searchQuery.update { query }
    }

    private fun searchChats(query: String) {
        viewModelScope.launch {
            val chats = chatRepository.searchChatsV2(query)
            _chatListState.update {
                it.copy(
                    chats = chats,
                    selectedChats = List(chats.size) { false },
                    loadError = null
                )
            }
        }
    }

    fun openDeleteWarningDialog() {
        closeSelectModelDialog()
        savedStateHandle[HOME_DELETE_WARNING_KEY] = true
        _showDeleteWarningDialog.update { true }
    }

    fun closeDeleteWarningDialog() {
        savedStateHandle[HOME_DELETE_WARNING_KEY] = false
        _showDeleteWarningDialog.update { false }
    }

    fun openSelectModelDialog() {
        viewModelScope.launch {
            val preferred = newChatBotHint.preferredPlatformUid()
            if (preferred != null) {
                val selected = _platformState.value.map { platform ->
                    platform.enabled && platform.uid == preferred
                }
                if (selected.any { it }) {
                    _chatListState.update { it.copy(selectedPlatforms = selected) }
                }
            }
            savedStateHandle[HOME_SELECT_MODEL_KEY] = true
            _showSelectModelDialog.update { true }
            disableSelectionMode()
        }
    }

    fun closeSelectModelDialog() {
        savedStateHandle[HOME_SELECT_MODEL_KEY] = false
        _showSelectModelDialog.update { false }
        _chatListState.update { it.copy(selectedPlatforms = List(it.selectedPlatforms.size) { false }) }
    }

    fun deleteSelectedChats() {
        viewModelScope.launch {
            val selectedChats = _chatListState.value.chats.filterIndexed { index, _ ->
                _chatListState.value.selectedChats.getOrElse(index) { false }
            }

            val chats = agentRunCoordinator.withChatGate(selectedChats.map { it.id }) {
                selectedChats.forEach { agentRunCoordinator.cancelChatAndJoin(it.id) }
                chatRepository.deleteChatsV2(selectedChats)
                chatRepository.fetchChatListV2()
            }
            _chatListState.update { it.copy(chats = chats) }
            disableSelectionMode()
        }
    }

    fun duplicateSelectedChat() {
        viewModelScope.launch {
            val selectedChats = _chatListState.value.chats.filterIndexed { index, _ ->
                _chatListState.value.selectedChats.getOrElse(index) { false }
            }
            val selectedChat = selectedChats.singleOrNull() ?: return@launch
            val chats = agentRunCoordinator.withChatGate(selectedChat.id) {
                if (agentRunCoordinator.hasActiveRuns(selectedChat.id)) return@withChatGate null
                chatRepository.duplicateChatV2(selectedChat)
                chatRepository.fetchChatListV2()
            } ?: return@launch
            _chatListState.update { it.copy(chats = chats) }
            disableSelectionMode()
        }
    }

    fun disableSelectionMode() {
        _chatListState.update {
            it.copy(
                selectedChats = List(it.chats.size) { false },
                isSelectionMode = false
            )
        }
    }

    fun disableSearchMode() {
        savedStateHandle[HOME_SEARCH_MODE_KEY] = false
        savedStateHandle[HOME_SEARCH_QUERY_KEY] = ""
        _chatListState.update { it.copy(isSearchMode = false) }
        _searchQuery.update { "" }
    }

    fun enableSelectionMode() {
        disableSearchMode()
        _chatListState.update { it.copy(isSelectionMode = true) }
    }

    fun enableSearchMode() {
        disableSelectionMode()
        savedStateHandle[HOME_SEARCH_MODE_KEY] = true
        _chatListState.update { it.copy(isSearchMode = true) }
    }

    fun fetchChats() {
        if (_chatListState.value.isSearchMode) {
            searchChats(_searchQuery.value)
            return
        }
        fetchChatsJob?.cancel()
        _chatListState.update { it.copy(isLoading = it.chats.isEmpty(), loadError = null) }
        fetchChatsJob = viewModelScope.launch {
            try {
                val chats = chatRepository.fetchChatListV2()
                refreshProjects()
                _chatListState.update {
                    it.copy(
                        chats = chats,
                        isLoading = false,
                        loadError = null,
                        selectedChats = List(chats.size) { false },
                        isSelectionMode = false
                    )
                }
                Log.d("chats", "${_chatListState.value.chats}")
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                _chatListState.update {
                    it.copy(
                        isLoading = false,
                        loadError = throwable.message.orEmpty()
                    )
                }
            }
        }
    }

    fun retryFetchChats() = fetchChats()

    fun fetchPlatformStatus() {
        viewModelScope.launch {
            val platforms = settingRepository.fetchPlatformV2s()
            _platformState.update { platforms }

            if (_chatListState.value.selectedPlatforms.size != platforms.size) {
                _chatListState.update { it.copy(selectedPlatforms = List(platforms.size) { false }) }
            }
        }
    }

    fun setProjectFilter(filter: ProjectFilter) {
        _projectFilter.value = filter
        disableSelectionMode()
    }

    fun openMoveDialog() {
        if (_chatListState.value.selectedChats.none { it }) return
        _showMoveDialog.value = true
    }

    fun closeMoveDialog() {
        _showMoveDialog.value = false
    }

    fun moveSelectedChats(projectId: Int?) {
        viewModelScope.launch {
            val selectedIds = _chatListState.value.chats.mapIndexedNotNull { index, chat ->
                chat.id.takeIf { _chatListState.value.selectedChats.getOrElse(index) { false } }
            }
            projectChatListSource.moveChats(selectedIds, projectId)
            _showMoveDialog.value = false
            fetchChats()
        }
    }

    private suspend fun refreshProjects() {
        try {
            val loaded = projectChatListSource.listProjects()
            _projects.value = loaded
            val filter = _projectFilter.value
            if (filter is ProjectFilter.Project && loaded.none { it.id == filter.id }) {
                _projectFilter.value = ProjectFilter.All
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // Chat list stays usable when project metadata cannot be read.
        }
    }

    fun selectChat(chatRoomIdx: Int) {
        if (chatRoomIdx < 0 || chatRoomIdx >= _chatListState.value.chats.size) return

        _chatListState.update {
            it.copy(
                selectedChats = it.selectedChats.mapIndexed { index, b ->
                    if (index == chatRoomIdx) {
                        !b
                    } else {
                        b
                    }
                }
            )
        }

        if (_chatListState.value.selectedChats.count { it } == 0) {
            disableSelectionMode()
        }
    }
}
