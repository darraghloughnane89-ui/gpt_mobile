package dev.chungjungsoo.gptmobile.presentation.ui.skill

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.skill.ApplyResult
import dev.chungjungsoo.gptmobile.data.skill.LearningLoop
import dev.chungjungsoo.gptmobile.data.skill.Skill
import dev.chungjungsoo.gptmobile.data.skill.SkillCodec
import dev.chungjungsoo.gptmobile.data.skill.SkillDraft
import dev.chungjungsoo.gptmobile.data.skill.SkillOrigin
import dev.chungjungsoo.gptmobile.data.skill.SkillProposal
import dev.chungjungsoo.gptmobile.data.skill.SkillRepository
import dev.chungjungsoo.gptmobile.data.skill.SkillWriteResult
import dev.chungjungsoo.gptmobile.data.skill.toDraft
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SkillLibraryState(
    val skills: List<Skill> = emptyList(),
    val message: String? = null,
    val importOpen: Boolean = false,
    val importText: String = ""
)

data class SkillEditorState(
    val name: String = "",
    val title: String = "",
    val description: String = "",
    val instructions: String = "",
    val tools: String = "",
    val examples: String = "",
    val pinned: Boolean = false,
    val message: String? = null,
    val exportText: String? = null
)

data class SkillInboxState(
    val proposals: List<SkillProposal> = emptyList(),
    val autoAccept: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class SkillLibraryViewModel @Inject constructor(
    private val repository: SkillRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SkillLibraryState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(skills = repository.listSkills()) }
        }
    }

    fun openImport() = _state.update { it.copy(importOpen = true, importText = "") }

    fun closeImport() = _state.update { it.copy(importOpen = false) }

    fun updateImportText(value: String) = _state.update { it.copy(importText = value) }

    fun import() {
        viewModelScope.launch {
            when (val result = repository.importMarkdown(_state.value.importText)) {
                is SkillWriteResult.Ok -> {
                    _state.update { it.copy(importOpen = false, importText = "", message = null) }
                    refresh()
                }
                is SkillWriteResult.Invalid -> _state.update { it.copy(message = result.message) }
            }
        }
    }

    fun duplicate(id: Long) {
        viewModelScope.launch {
            when (val result = repository.duplicate(id)) {
                is SkillWriteResult.Ok -> refresh()
                is SkillWriteResult.Invalid -> _state.update { it.copy(message = result.message) }
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            when (val result = repository.delete(id)) {
                is SkillWriteResult.Ok -> refresh()
                is SkillWriteResult.Invalid -> _state.update { it.copy(message = result.message) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@HiltViewModel
class SkillEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SkillRepository
) : ViewModel() {
    private val skillId: Long = savedStateHandle.get<Long>("skillId") ?: -1L
    private val _state = MutableStateFlow(SkillEditorState())
    val state = _state.asStateFlow()
    private var existingOrigin: String? = null

    init {
        if (skillId > 0) {
            viewModelScope.launch {
                val skill = repository.get(skillId) ?: return@launch
                existingOrigin = skill.origin
                val draft = skill.toDraft()
                _state.value = SkillEditorState(
                    name = draft.name,
                    title = draft.title,
                    description = draft.description,
                    instructions = draft.instructions,
                    tools = draft.tools.joinToString(", "),
                    examples = draft.examples.joinToString("\n"),
                    pinned = draft.pinned
                )
            }
        }
    }

    fun update(transform: (SkillEditorState) -> SkillEditorState) = _state.update(transform)

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val current = _state.value
            val result = repository.save(
                id = skillId.takeIf { it > 0 },
                draft = SkillDraft(
                    name = current.name,
                    title = current.title,
                    description = current.description,
                    instructions = current.instructions,
                    tools = SkillCodec.decodeList(current.tools),
                    examples = current.examples.lines().map { it.trim() }.filter { it.isNotEmpty() },
                    pinned = current.pinned,
                    origin = existingOrigin ?: SkillOrigin.USER
                )
            )
            when (result) {
                is SkillWriteResult.Ok -> onSaved()
                is SkillWriteResult.Invalid -> _state.update { it.copy(message = result.message) }
            }
        }
    }

    fun export() {
        if (skillId <= 0) return
        viewModelScope.launch {
            _state.update { it.copy(exportText = repository.exportMarkdown(skillId)) }
        }
    }

    fun consumeExport() = _state.update { it.copy(exportText = null) }
}

@HiltViewModel
class SkillInboxViewModel @Inject constructor(
    private val repository: SkillRepository,
    private val learningLoop: LearningLoop
) : ViewModel() {
    private val _state = MutableStateFlow(SkillInboxState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            learningLoop.fileCuratorSuggestions()
            _state.value = SkillInboxState(
                proposals = repository.pendingProposals(),
                autoAccept = repository.autoAccept()
            )
        }
    }

    fun setAutoAccept(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoAccept(enabled)
            _state.update { it.copy(autoAccept = enabled) }
        }
    }

    fun approve(proposal: SkillProposal, draft: SkillDraft) {
        viewModelScope.launch {
            when (val result = repository.accept(proposal.id, draft)) {
                is ApplyResult.Saved -> refresh()
                is ApplyResult.Blocked -> _state.update { it.copy(message = result.message) }
            }
        }
    }

    fun reject(id: Long) {
        viewModelScope.launch {
            repository.reject(id)
            refresh()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
