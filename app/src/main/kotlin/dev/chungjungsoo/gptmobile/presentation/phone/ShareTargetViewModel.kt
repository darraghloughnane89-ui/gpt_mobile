package dev.chungjungsoo.gptmobile.presentation.phone

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.phone.ImportedShare
import dev.chungjungsoo.gptmobile.data.phone.PhoneHandoffStore
import dev.chungjungsoo.gptmobile.data.phone.ShareIntentParser
import dev.chungjungsoo.gptmobile.data.phone.SharedAttachmentImporter
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareOpenRequest(
    val chatId: Int,
    val enabledPlatforms: List<String>
)

data class ShareTargetUiState(
    val isLoading: Boolean = true,
    val importing: Boolean = false,
    val error: String? = null,
    val preview: String = "",
    val attachmentCount: Int = 0,
    val warnings: List<String> = emptyList(),
    val chats: List<ShareChatRow> = emptyList(),
    val platforms: List<PlatformV2> = emptyList(),
    val selectedPlatforms: List<Boolean> = emptyList(),
    val showPlatformDialog: Boolean = false
)

@HiltViewModel
class ShareTargetViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val settingRepository: SettingRepository,
    private val importer: SharedAttachmentImporter,
    private val handoffStore: PhoneHandoffStore
) : ViewModel() {
    private val _state = MutableStateFlow(ShareTargetUiState())
    val state = _state.asStateFlow()

    private val _openChat = MutableSharedFlow<ShareOpenRequest>(extraBufferCapacity = 1)
    val openChat = _openChat.asSharedFlow()

    private var imported: ImportedShare? = null
    private var chatsById: Map<Int, List<String>> = emptyMap()
    private var loadStarted = false

    fun load(snapshot: ShareIntentParser.Snapshot) {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch {
            val parsed = ShareIntentParser.parse(snapshot)
            if (parsed.isEmpty()) {
                _state.value = ShareTargetUiState(
                    isLoading = false,
                    error = context.getString(R.string.share_nothing_usable)
                )
                return@launch
            }
            val result = importer.import(parsed)
            if (result.composerText.isBlank() && result.filePaths.isEmpty()) {
                _state.value = ShareTargetUiState(
                    isLoading = false,
                    error = result.warnings.firstOrNull() ?: context.getString(R.string.share_nothing_usable)
                )
                return@launch
            }
            imported = result
            val rooms = chatRepository.fetchChatListV2()
            chatsById = rooms.associate { it.id to it.enabledPlatform }
            val platforms = settingRepository.fetchPlatformV2s().filter { it.enabled }
            _state.value = ShareTargetUiState(
                isLoading = false,
                preview = result.composerText,
                attachmentCount = result.filePaths.size,
                warnings = result.warnings,
                chats = rooms.map { ShareChatRow(id = it.id, title = it.title) },
                platforms = platforms,
                selectedPlatforms = List(platforms.size) { true }
            )
        }
    }

    fun onNewChatClicked() {
        val platforms = _state.value.platforms
        when {
            platforms.isEmpty() -> _state.update { it.copy(error = context.getString(R.string.no_platforms_for_share)) }
            platforms.size == 1 -> openNewChat(listOf(platforms.first().uid))
            else -> _state.update { it.copy(showPlatformDialog = true) }
        }
    }

    fun togglePlatform(index: Int) {
        _state.update { state ->
            state.copy(
                selectedPlatforms = state.selectedPlatforms.mapIndexed { itemIndex, selected ->
                    if (itemIndex == index) !selected else selected
                }
            )
        }
    }

    fun confirmPlatforms(platformUids: List<String>) {
        _state.update { it.copy(showPlatformDialog = false) }
        if (platformUids.isEmpty()) return
        openNewChat(platformUids)
    }

    fun dismissPlatformDialog() {
        _state.update { it.copy(showPlatformDialog = false) }
    }

    fun onExistingChat(chatId: Int) {
        val platforms = chatsById[chatId].orEmpty()
        if (platforms.isEmpty()) {
            _state.update { it.copy(error = context.getString(R.string.chat_link_unavailable)) }
            return
        }
        val share = imported ?: return
        handoffStore.offer(
            PhoneHandoffStore.Handoff(
                targetChatId = chatId,
                text = share.composerText,
                filePaths = share.filePaths
            )
        )
        _openChat.tryEmit(ShareOpenRequest(chatId, platforms))
    }

    private fun openNewChat(platformUids: List<String>) {
        val share = imported ?: return
        handoffStore.offer(
            PhoneHandoffStore.Handoff(
                targetChatId = 0,
                text = share.composerText,
                filePaths = share.filePaths
            )
        )
        _openChat.tryEmit(ShareOpenRequest(chatId = 0, enabledPlatforms = platformUids))
    }
}
