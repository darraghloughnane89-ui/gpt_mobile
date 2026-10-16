package dev.chungjungsoo.gptmobile.presentation.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.agent.AgentRunCoordinator
import dev.chungjungsoo.gptmobile.data.bot.NewChatBotHint
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.library.ChatDesk
import dev.chungjungsoo.gptmobile.data.library.ChatLibraryState
import dev.chungjungsoo.gptmobile.data.library.ChatListFilter
import dev.chungjungsoo.gptmobile.data.library.FolderNameIssue
import dev.chungjungsoo.gptmobile.data.library.addFolder
import dev.chungjungsoo.gptmobile.data.library.archive
import dev.chungjungsoo.gptmobile.data.library.deleteFolder
import dev.chungjungsoo.gptmobile.data.library.folderNameIssue
import dev.chungjungsoo.gptmobile.data.library.forget
import dev.chungjungsoo.gptmobile.data.library.moveToFolder
import dev.chungjungsoo.gptmobile.data.library.pin
import dev.chungjungsoo.gptmobile.data.library.projectChatList
import dev.chungjungsoo.gptmobile.data.library.renameFolder
import dev.chungjungsoo.gptmobile.data.library.restore
import dev.chungjungsoo.gptmobile.data.library.unpin
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import java.util.UUID
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
    private val chatDesk: ChatDesk,
    private val newChatBotHint: NewChatBotHint = NewChatBotHint.None
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
        val isLibraryFilterEmpty: Boolean = false,
        val selectedPlatforms: List<Boolean> = listOf(),
        val selectedChats: List<Boolean> = listOf()
    )

    private val _chatListState = MutableStateFlow(ChatListState())
    val chatListState: StateFlow<ChatListState> = _chatListState.asStateFlow()

    private val _platformState = MutableStateFlow(listOf<PlatformV2>())
    val platformState = _platformState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _libraryState = MutableStateFlow(ChatLibraryState())
    val libraryState = _libraryState.asStateFlow()

    private val _libraryFilter = MutableStateFlow<ChatListFilter>(ChatListFilter.Inbox)
    val libraryFilter = _libraryFilter.asStateFlow()

    private val _searchExcerpts = MutableStateFlow<Map<Int, String>>(emptyMap())
    val searchExcerpts = _searchExcerpts.asStateFlow()

    private var rawChats: List<ChatRoomV2> = emptyList()

    private val _showSelectModelDialog = MutableStateFlow(false)
    val showSelectModelDialog: StateFlow<Boolean> = _showSelectModelDialog.asStateFlow()

    private val _showDeleteWarningDialog = MutableStateFlow(false)
    val showDeleteWarningDialog: StateFlow<Boolean> = _showDeleteWarningDialog.asStateFlow()

    private val _activeChatIds = MutableStateFlow<Set<Int>>(emptySet())
    val activeChatIds = _activeChatIds.asStateFlow()
    private var fetchChatsJob: Job? = null

    init {
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
        chatDesk.library
            .onEach { library ->
                _libraryState.value = library
                if (!_chatListState.value.isLoading) {
                    applyProjection(resetSelection = false)
                }
            }
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
        _searchQuery.update { query }
    }

    private fun searchChats(query: String) {
        viewModelScope.launch {
            val chats = chatRepository.searchChatsV2(query)
            val excerpts = if (query.isBlank()) emptyMap() else chatDesk.searchExcerpts(query)
            rawChats = chats
            _searchExcerpts.value = excerpts
            _chatListState.update { it.copy(loadError = null) }
            applyProjection(resetSelection = true)
        }
    }

    fun openDeleteWarningDialog() {
        closeSelectModelDialog()
        _showDeleteWarningDialog.update { true }
    }

    fun closeDeleteWarningDialog() {
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
            _showSelectModelDialog.update { true }
            disableSelectionMode()
        }
    }

    fun closeSelectModelDialog() {
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
                chatDesk.updateLibrary { state ->
                    selectedChats.fold(state) { acc, chat -> acc.forget(chat.id) }
                }
                chatRepository.fetchChatListV2()
            }
            rawChats = chats
            applyProjection(resetSelection = true)
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
            rawChats = chats
            applyProjection(resetSelection = true)
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
        _chatListState.update { it.copy(isSearchMode = false) }
        _searchQuery.update { "" }
    }

    fun enableSelectionMode() {
        disableSearchMode()
        _chatListState.update { it.copy(isSelectionMode = true) }
    }

    fun enableSearchMode() {
        disableSelectionMode()
        _chatListState.update { it.copy(isSearchMode = true) }
    }

    fun fetchChats() {
        fetchChatsJob?.cancel()
        _chatListState.update { it.copy(isLoading = it.chats.isEmpty(), loadError = null) }
        fetchChatsJob = viewModelScope.launch {
            try {
                purgeTemporaryChats()
                val chats = chatRepository.fetchChatListV2()
                rawChats = chats
                _searchExcerpts.value = emptyMap()
                _chatListState.update { it.copy(isLoading = false, loadError = null) }
                applyProjection(resetSelection = true)
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

    fun setLibraryFilter(filter: ChatListFilter) {
        _libraryFilter.value = filter
        applyProjection(resetSelection = true)
    }

    fun togglePinSelectedChats() {
        val selected = selectedRooms()
        if (selected.isEmpty()) return
        val unpinAll = selected.all { it.id in _libraryState.value.pinnedChatIds }
        viewModelScope.launch {
            chatDesk.updateLibrary { state ->
                selected.fold(state) { acc, chat ->
                    if (unpinAll) acc.unpin(chat.id) else acc.pin(chat.id)
                }
            }
        }
    }

    fun toggleArchiveSelectedChats() {
        val selected = selectedRooms()
        if (selected.isEmpty()) return
        val restoreAll = selected.all { it.id in _libraryState.value.archivedChatIds }
        viewModelScope.launch {
            chatDesk.updateLibrary { state ->
                selected.fold(state) { acc, chat ->
                    if (restoreAll) acc.restore(chat.id) else acc.archive(chat.id)
                }
            }
        }
    }

    fun moveSelectedChats(folderId: String?) {
        val ids = selectedRooms().map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            chatDesk.updateLibrary { state ->
                ids.fold(state) { acc, id -> acc.moveToFolder(id, folderId) }
            }
        }
    }

    fun createFolder(name: String): FolderNameIssue? {
        val issue = folderNameIssue(_libraryState.value.folders, name)
        if (issue != null) return issue
        val folderId = UUID.randomUUID().toString()
        viewModelScope.launch {
            chatDesk.updateLibrary { state ->
                val update = state.addFolder(folderId, name)
                if (update.issue != null) state else update.state
            }
            _libraryFilter.value = ChatListFilter.Folder(folderId)
            applyProjection(resetSelection = true)
        }
        return null
    }

    fun createFolderForSelected(name: String): FolderNameIssue? {
        val issue = folderNameIssue(_libraryState.value.folders, name)
        if (issue != null) return issue
        val ids = selectedRooms().map { it.id }
        val folderId = UUID.randomUUID().toString()
        viewModelScope.launch {
            chatDesk.updateLibrary { state ->
                val created = state.addFolder(folderId, name)
                if (created.issue != null) {
                    state
                } else {
                    ids.fold(created.state) { acc, id -> acc.moveToFolder(id, folderId) }
                }
            }
            _libraryFilter.value = ChatListFilter.Folder(folderId)
            applyProjection(resetSelection = true)
        }
        return null
    }

    fun renameFolder(folderId: String, name: String): FolderNameIssue? {
        val issue = folderNameIssue(_libraryState.value.folders, name, ignoreId = folderId)
        if (issue != null) return issue
        viewModelScope.launch {
            chatDesk.updateLibrary { state ->
                val update = state.renameFolder(folderId, name)
                if (update.issue != null) state else update.state
            }
        }
        return null
    }

    fun deleteLibraryFolder(folderId: String) {
        viewModelScope.launch {
            chatDesk.updateLibrary { it.deleteFolder(folderId) }
            if (_libraryFilter.value == ChatListFilter.Folder(folderId)) {
                _libraryFilter.value = ChatListFilter.Inbox
            }
            applyProjection(resetSelection = true)
        }
    }

    private fun selectedRooms(): List<ChatRoomV2> {
        val state = _chatListState.value
        return state.chats.filterIndexed { index, _ ->
            state.selectedChats.getOrElse(index) { false }
        }
    }

    private suspend fun purgeTemporaryChats() {
        val ids = chatDesk.currentLibrary().temporaryChatIds
        if (ids.isEmpty()) return
        val all = chatRepository.fetchChatListV2()
        val doomed = all.filter { it.id in ids }
        if (doomed.isNotEmpty()) {
            chatRepository.deleteChatsV2(doomed)
        }
        val presentIds = all.map { it.id }.toSet()
        val forgetIds = doomed.map { it.id }.toSet() + (ids - presentIds)
        if (forgetIds.isNotEmpty()) {
            chatDesk.updateLibrary { state ->
                forgetIds.fold(state) { acc, id -> acc.forget(id) }
            }
        }
    }

    private fun applyProjection(resetSelection: Boolean) {
        val searching = _chatListState.value.isSearchMode && _searchQuery.value.isNotBlank()
        val library = _libraryState.value
        val visible = projectChatList(
            chats = rawChats,
            chatId = { it.id },
            updatedAt = { it.updatedAt },
            library = library,
            filter = _libraryFilter.value,
            searching = searching
        )
        _chatListState.update { current ->
            val idsChanged = current.chats.map { it.id } != visible.map { it.id }
            val clearSelection = resetSelection || idsChanged
            current.copy(
                chats = visible,
                selectedChats = if (clearSelection) List(visible.size) { false } else current.selectedChats,
                isSelectionMode = if (clearSelection) false else current.isSelectionMode,
                isLibraryFilterEmpty = !searching &&
                    visible.isEmpty() &&
                    rawChats.any { it.id !in library.temporaryChatIds }
            )
        }
    }
}
