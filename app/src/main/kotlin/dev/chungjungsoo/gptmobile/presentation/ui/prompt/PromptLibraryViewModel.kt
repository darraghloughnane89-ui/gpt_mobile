package dev.chungjungsoo.gptmobile.presentation.ui.prompt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.prompt.PromptImportCounts
import dev.chungjungsoo.gptmobile.data.prompt.PromptImportOutcome
import dev.chungjungsoo.gptmobile.data.prompt.PromptLibraryRepository
import dev.chungjungsoo.gptmobile.data.prompt.SavedPrompt
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PromptDraft(
    val id: String? = null,
    val title: String = "",
    val body: String = "",
    val favourite: Boolean = false,
    val isNew: Boolean = true
)

sealed interface PromptLibraryStatus {
    data object NeedsText : PromptLibraryStatus
    data object Saved : PromptLibraryStatus
    data object Unreadable : PromptLibraryStatus
    data class Imported(val counts: PromptImportCounts) : PromptLibraryStatus
}

@HiltViewModel
class PromptLibraryViewModel @Inject constructor(
    private val repository: PromptLibraryRepository
) : ViewModel() {
    val prompts = repository.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _draft = MutableStateFlow<PromptDraft?>(null)
    val draft = _draft.asStateFlow()

    private val _status = MutableStateFlow<PromptLibraryStatus?>(null)
    val status = _status.asStateFlow()

    fun startCreate() {
        _draft.value = PromptDraft()
    }

    fun startEdit(id: String) {
        val prompt = prompts.value.firstOrNull { it.id == id } ?: return
        _draft.value = PromptDraft(
            id = prompt.id,
            title = prompt.title,
            body = prompt.body,
            favourite = prompt.favourite,
            isNew = false
        )
    }

    fun updateDraft(title: String? = null, body: String? = null, favourite: Boolean? = null) {
        _draft.update { current ->
            current?.copy(
                title = title ?: current.title,
                body = body ?: current.body,
                favourite = favourite ?: current.favourite
            )
        }
    }

    fun dismissDraft() {
        _draft.value = null
    }

    fun saveDraft() {
        val current = _draft.value ?: return
        if (current.title.isBlank() || current.body.isBlank()) {
            _status.value = PromptLibraryStatus.NeedsText
            return
        }
        viewModelScope.launch {
            repository.save(
                SavedPrompt(
                    id = current.id.orEmpty(),
                    title = current.title,
                    body = current.body,
                    favourite = current.favourite
                )
            )
            _draft.value = null
            _status.value = PromptLibraryStatus.Saved
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            if (_draft.value?.id == id) _draft.value = null
        }
    }

    fun setFavourite(id: String, favourite: Boolean) {
        viewModelScope.launch { repository.setFavourite(id, favourite) }
    }

    fun importJson(raw: String) {
        viewModelScope.launch {
            _status.value = when (val outcome = repository.importJson(raw)) {
                PromptImportOutcome.Unreadable -> PromptLibraryStatus.Unreadable
                is PromptImportOutcome.Success -> PromptLibraryStatus.Imported(outcome.counts)
            }
        }
    }

    suspend fun exportJson(): String = repository.exportJson()

    fun consumeStatus() {
        _status.value = null
    }
}
