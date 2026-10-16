package dev.chungjungsoo.gptmobile.presentation.ui.portable

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.bot.BotDraft
import dev.chungjungsoo.gptmobile.data.portable.PortableDocuments
import dev.chungjungsoo.gptmobile.data.portable.PortableFailure
import dev.chungjungsoo.gptmobile.data.portable.PortableFileIntake
import dev.chungjungsoo.gptmobile.data.portable.PortableImportInbox
import dev.chungjungsoo.gptmobile.data.portable.PortableImporter
import dev.chungjungsoo.gptmobile.data.portable.PortableKind
import dev.chungjungsoo.gptmobile.data.portable.PortableLibraryFull
import dev.chungjungsoo.gptmobile.data.portable.PortableLibraryStore
import dev.chungjungsoo.gptmobile.data.portable.PortableLimits
import dev.chungjungsoo.gptmobile.data.portable.PortablePromptBody
import dev.chungjungsoo.gptmobile.data.portable.PortableResult
import dev.chungjungsoo.gptmobile.data.portable.PortableSkillBody
import dev.chungjungsoo.gptmobile.data.portable.StoredPrompt
import dev.chungjungsoo.gptmobile.data.portable.StoredSkill
import dev.chungjungsoo.gptmobile.data.portable.portableSlug
import dev.chungjungsoo.gptmobile.data.portable.untrustedText
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class PortableImportBridgeViewModel @Inject constructor(
    val inbox: PortableImportInbox,
    val intake: PortableFileIntake
) : ViewModel()

@HiltViewModel
class PortableImportViewModel @Inject constructor(
    private val inbox: PortableImportInbox,
    private val importer: PortableImporter
) : ViewModel() {
    val preview = inbox.preview
    val failure = inbox.failure

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    private val _finished = MutableStateFlow<PortableKind?>(null)
    val finished = _finished.asStateFlow()

    fun confirm() {
        val pending = inbox.preview.value ?: return
        if (_saving.value) return
        viewModelScope.launch {
            _saving.value = true
            try {
                importer.commit(pending)
                inbox.clear()
                _finished.value = pending.kind
            } catch (_: PortableLibraryFull) {
                inbox.offer(PortableResult.Failure(PortableFailure.LIBRARY_FULL))
            } finally {
                _saving.value = false
            }
        }
    }

    fun dismiss() {
        inbox.clear()
    }
}

data class SkillLibraryState(
    val skills: List<StoredSkill> = emptyList()
)

@HiltViewModel
class PortableSkillLibraryViewModel @Inject constructor(
    private val library: PortableLibraryStore
) : ViewModel() {
    private val _state = MutableStateFlow(SkillLibraryState())
    val state = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = SkillLibraryState(library.snapshot().skills)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            library.deleteSkill(id)
            refresh()
        }
    }

    suspend fun exportJson(id: String): String? = library.skill(id)?.let { skill ->
        PortableDocuments.exportSkill(skill.toBody())
    }
}

data class PromptLibraryState(
    val prompts: List<StoredPrompt> = emptyList()
)

@HiltViewModel
class SavedPromptLibraryViewModel @Inject constructor(
    private val library: PortableLibraryStore
) : ViewModel() {
    private val _state = MutableStateFlow(PromptLibraryState())
    val state = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = PromptLibraryState(library.snapshot().prompts)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            library.deletePrompt(id)
            refresh()
        }
    }

    suspend fun exportJson(id: String): String? = library.prompt(id)?.let { prompt ->
        PortableDocuments.exportPrompt(prompt.toBody())
    }
}

enum class PortableEditorError {
    NAME,
    DESCRIPTION,
    INSTRUCTIONS,
    TEXT,
    LIBRARY
}

data class SkillEditorState(
    val name: String = "",
    val title: String = "",
    val description: String = "",
    val instructions: String = "",
    val tools: String = "",
    val examples: String = "",
    val untrusted: Boolean = false,
    val loading: Boolean = true,
    val missing: Boolean = false,
    val error: PortableEditorError? = null
)

@HiltViewModel
class PortableSkillEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: PortableLibraryStore
) : ViewModel() {
    private val skillId: String? = savedStateHandle.get<String>("skillId")?.takeUnless { it == "new" }
    private val _state = MutableStateFlow(SkillEditorState())
    val state = _state.asStateFlow()
    val isEditing: Boolean = skillId != null

    init {
        viewModelScope.launch { load() }
    }

    fun update(transform: (SkillEditorState) -> SkillEditorState) {
        _state.update { transform(it).copy(error = null) }
    }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        val slug = portableSlug(current.name.ifBlank { current.title })
        if (slug == null) {
            _state.update { it.copy(error = PortableEditorError.NAME) }
            return
        }
        val description = untrustedText(current.description, PortableLimits.MAX_DESCRIPTION)
        val instructions = untrustedText(current.instructions, PortableLimits.MAX_INSTRUCTIONS)
        if (description.isBlank()) {
            _state.update { it.copy(error = PortableEditorError.DESCRIPTION) }
            return
        }
        if (instructions.isBlank()) {
            _state.update { it.copy(error = PortableEditorError.INSTRUCTIONS) }
            return
        }
        viewModelScope.launch {
            val existing = skillId?.let { library.skill(it) }
            val now = System.currentTimeMillis() / 1000
            val skill = StoredSkill(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = slug,
                title = untrustedText(current.title, 80).ifBlank { slug },
                description = description,
                instructions = instructions,
                tools = splitList(current.tools),
                examples = splitList(current.examples),
                untrusted = existing?.untrusted == true,
                createdAt = existing?.createdAt ?: now
            )
            try {
                library.upsertSkill(skill)
                onSaved()
            } catch (_: PortableLibraryFull) {
                _state.update { it.copy(error = PortableEditorError.LIBRARY) }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = skillId ?: return
        viewModelScope.launch {
            library.deleteSkill(id)
            onDeleted()
        }
    }

    fun exportJson(): String? {
        val current = _state.value
        val slug = portableSlug(current.name.ifBlank { current.title }) ?: return null
        val description = current.description.trim()
        val instructions = current.instructions.trim()
        if (description.isBlank() || instructions.isBlank()) return null
        return PortableDocuments.exportSkill(
            PortableSkillBody(
                name = slug,
                title = current.title.trim().ifBlank { slug },
                description = description,
                instructions = instructions,
                tools = splitList(current.tools),
                examples = splitList(current.examples)
            )
        )
    }

    private suspend fun load() {
        val existing = skillId?.let { library.skill(it) }
        _state.value = if (skillId != null && existing == null) {
            SkillEditorState(loading = false, missing = true)
        } else if (existing == null) {
            SkillEditorState(loading = false)
        } else {
            SkillEditorState(
                name = existing.name,
                title = existing.title,
                description = existing.description,
                instructions = existing.instructions,
                tools = existing.tools.joinToString("\n"),
                examples = existing.examples.joinToString("\n"),
                untrusted = existing.untrusted,
                loading = false
            )
        }
    }
}

data class PromptEditorState(
    val title: String = "",
    val text: String = "",
    val untrusted: Boolean = false,
    val loading: Boolean = true,
    val missing: Boolean = false,
    val error: PortableEditorError? = null
)

@HiltViewModel
class SavedPromptEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: PortableLibraryStore
) : ViewModel() {
    private val promptId: String? = savedStateHandle.get<String>("promptId")?.takeUnless { it == "new" }
    private val _state = MutableStateFlow(PromptEditorState())
    val state = _state.asStateFlow()
    val isEditing: Boolean = promptId != null

    init {
        viewModelScope.launch { load() }
    }

    fun update(transform: (PromptEditorState) -> PromptEditorState) {
        _state.update { transform(it).copy(error = null) }
    }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        val text = untrustedText(current.text, PortableLimits.MAX_INSTRUCTIONS)
        if (text.isBlank()) {
            _state.update { it.copy(error = PortableEditorError.TEXT) }
            return
        }
        val fallback = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val title = untrustedText(current.title, 80).ifBlank { untrustedText(fallback, 80) }.ifBlank { "Prompt" }
        viewModelScope.launch {
            val existing = promptId?.let { library.prompt(it) }
            val now = System.currentTimeMillis() / 1000
            val prompt = StoredPrompt(
                id = existing?.id ?: UUID.randomUUID().toString(),
                title = title,
                text = text,
                untrusted = existing?.untrusted == true,
                createdAt = existing?.createdAt ?: now
            )
            try {
                library.upsertPrompt(prompt)
                onSaved()
            } catch (_: PortableLibraryFull) {
                _state.update { it.copy(error = PortableEditorError.LIBRARY) }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = promptId ?: return
        viewModelScope.launch {
            library.deletePrompt(id)
            onDeleted()
        }
    }

    fun exportJson(): String? {
        val text = _state.value.text.trim()
        if (text.isBlank()) return null
        val title = _state.value.title.trim().ifBlank {
            text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        }.ifBlank { "Prompt" }
        return PortableDocuments.exportPrompt(PortablePromptBody(title, text))
    }

    private suspend fun load() {
        val existing = promptId?.let { library.prompt(it) }
        _state.value = if (promptId != null && existing == null) {
            PromptEditorState(loading = false, missing = true)
        } else if (existing == null) {
            PromptEditorState(loading = false)
        } else {
            PromptEditorState(
                title = existing.title,
                text = existing.text,
                untrusted = existing.untrusted,
                loading = false
            )
        }
    }
}

internal fun splitList(raw: String): List<String> = raw.split('\n', ',')
    .map { untrustedText(it, PortableLimits.MAX_EXAMPLE) }
    .filter { it.isNotEmpty() }
    .distinct()
    .take(PortableLimits.MAX_TOOLS)

fun botExportJson(draft: BotDraft): String? {
    if (draft.sanitized().name.isBlank()) return null
    return PortableDocuments.exportBot(draft)
}

fun portableLink(json: String): String? {
    val preview = (PortableDocuments.parse(json) as? PortableResult.Ok)?.preview
        ?: return null
    return PortableDocuments.shareLink(preview)
}
