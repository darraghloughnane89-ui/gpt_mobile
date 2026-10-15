package dev.chungjungsoo.gptmobile.presentation.ui.project

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.Project
import dev.chungjungsoo.gptmobile.data.database.entity.ProjectFile
import dev.chungjungsoo.gptmobile.data.project.ProjectColors
import dev.chungjungsoo.gptmobile.data.project.ProjectContextLimits
import dev.chungjungsoo.gptmobile.data.project.ProjectIcons
import dev.chungjungsoo.gptmobile.data.project.ProjectImportError
import dev.chungjungsoo.gptmobile.data.project.ProjectRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository
) : ViewModel() {
    val projects = projectRepository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(project: Project) {
        viewModelScope.launch { projectRepository.deleteProject(project.id) }
    }
}

data class ProjectEditorState(
    val name: String = "",
    val instructions: String = "",
    val icon: String = ProjectIcons.FOLDER,
    val color: Int = ProjectColors.palette.first(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProjectEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private val projectId: Int = savedStateHandle.get<Int>("projectId") ?: -1
    val isNew: Boolean = projectId <= 0

    private val _state = MutableStateFlow(ProjectEditorState(isLoading = !isNew))
    val state = _state.asStateFlow()

    init {
        if (!isNew) {
            viewModelScope.launch {
                val project = projectRepository.getProject(projectId)
                _state.value = if (project == null) {
                    ProjectEditorState(error = context.getString(R.string.project_missing))
                } else {
                    ProjectEditorState(
                        name = project.name,
                        instructions = project.instructions,
                        icon = project.icon,
                        color = project.color
                    )
                }
            }
        }
    }

    fun updateName(value: String) {
        _state.value = _state.value.copy(name = value.take(ProjectContextLimits.MAX_NAME_CHARS), error = null)
    }

    fun updateInstructions(value: String) {
        _state.value = _state.value.copy(instructions = value.take(ProjectContextLimits.MAX_INSTRUCTION_CHARS), error = null)
    }

    fun updateIcon(value: String) {
        _state.value = _state.value.copy(icon = value)
    }

    fun updateColor(value: Int) {
        _state.value = _state.value.copy(color = value)
    }

    fun save(onSaved: (Int) -> Unit) {
        val current = _state.value
        val name = current.name.trim()
        if (name.isEmpty()) {
            _state.value = current.copy(error = context.getString(R.string.project_name_required))
            return
        }
        viewModelScope.launch {
            val saved = if (isNew) {
                projectRepository.createProject(name, current.icon, current.color, current.instructions)
            } else {
                val existing = projectRepository.getProject(projectId) ?: return@launch
                val updated = existing.copy(
                    name = name,
                    icon = current.icon,
                    color = current.color,
                    instructions = current.instructions
                )
                projectRepository.updateProject(updated)
                updated
            }
            onSaved(saved.id)
        }
    }
}

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val projectRepository: ProjectRepository,
    private val settingRepository: SettingRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private val projectId: Int = checkNotNull(savedStateHandle.get<Int>("projectId"))

    val project = projectRepository.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val files = projectRepository.observeFiles(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val chats = projectRepository.observeChats(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _platforms = MutableStateFlow<List<PlatformV2>>(emptyList())
    val platforms = _platforms.asStateFlow()

    private val _selectedPlatforms = MutableStateFlow<List<Boolean>>(emptyList())
    val selectedPlatforms = _selectedPlatforms.asStateFlow()

    private val _showPlatformDialog = MutableStateFlow(false)
    val showPlatformDialog = _showPlatformDialog.asStateFlow()

    private val _showAddChat = MutableStateFlow(false)
    val showAddChat = _showAddChat.asStateFlow()

    private val _availableChats = MutableStateFlow<List<ChatRoomV2>>(emptyList())
    val availableChats = _availableChats.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()

    fun refreshPlatforms() {
        viewModelScope.launch {
            val loaded = settingRepository.fetchPlatformV2s()
            _platforms.value = loaded
            if (_selectedPlatforms.value.size != loaded.size) {
                _selectedPlatforms.value = List(loaded.size) { false }
            }
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            val error = projectRepository.importFile(projectId, uri)
            _notice.value = when (error) {
                null -> null
                ProjectImportError.TOO_LARGE -> context.getString(R.string.project_file_too_large)
                ProjectImportError.UNSUPPORTED -> context.getString(R.string.project_file_unsupported)
                ProjectImportError.UNREADABLE -> context.getString(R.string.project_file_unreadable)
                ProjectImportError.EMPTY -> context.getString(R.string.project_file_empty)
            }
        }
    }

    fun consumeNotice() {
        _notice.value = null
    }

    fun deleteFile(file: ProjectFile) {
        viewModelScope.launch { projectRepository.deleteFile(file) }
    }

    fun removeChat(chatId: Int) {
        viewModelScope.launch { projectRepository.moveChats(listOf(chatId), null) }
    }

    fun openAddChat() {
        viewModelScope.launch {
            _availableChats.value = projectRepository.listChats().filter { it.projectId != projectId }
            _showAddChat.value = true
        }
    }

    fun closeAddChat() {
        _showAddChat.value = false
    }

    fun addChat(chatId: Int) {
        viewModelScope.launch {
            projectRepository.moveChats(listOf(chatId), projectId)
            _showAddChat.value = false
        }
    }

    fun openPlatformDialog() {
        refreshPlatforms()
        _showPlatformDialog.value = true
    }

    fun closePlatformDialog() {
        _showPlatformDialog.value = false
        _selectedPlatforms.value = List(_platforms.value.size) { false }
    }

    fun togglePlatform(index: Int) {
        _selectedPlatforms.value = _selectedPlatforms.value.mapIndexed { itemIndex, selected ->
            if (itemIndex == index) !selected else selected
        }
    }

    fun deleteProject(onDeleted: () -> Unit) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
            onDeleted()
        }
    }
}
