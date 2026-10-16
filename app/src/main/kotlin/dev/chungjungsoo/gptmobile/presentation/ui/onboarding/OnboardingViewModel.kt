package dev.chungjungsoo.gptmobile.presentation.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.onboarding.MiMoSuggestion
import dev.chungjungsoo.gptmobile.data.onboarding.OnboardingArchiveImport
import dev.chungjungsoo.gptmobile.data.onboarding.OnboardingStore
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveFailure
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveOutcome
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep {
    Welcome,
    Provider,
    Import,
    Voice,
    Explore
}

enum class OnboardingDestination {
    Home,
    Bots,
    Projects
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val providerName: String = MiMoSuggestion.NAME,
    val apiUrl: String = MiMoSuggestion.API_URL,
    val model: String = MiMoSuggestion.MODEL,
    val apiKey: String = "",
    val voiceEnabled: Boolean = false,
    val importedProjects: Int? = null,
    val importedChats: Int = 0,
    val importFailure: ProjectArchiveFailure? = null,
    val isSaving: Boolean = false,
    val isImporting: Boolean = false,
    val saveFailed: Boolean = false
) {
    val busy: Boolean
        get() = isSaving || isImporting
    val stepNumber: Int = step.ordinal + 1
    val stepCount: Int = OnboardingStep.entries.size
    val providerReady: Boolean = MiMoSuggestion.isReady(providerName, apiUrl, model, apiKey)
    val usingMiMo: Boolean = MiMoSuggestion.matches(providerName, apiUrl, model)
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val onboardingStore: OnboardingStore,
    private val archiveImport: OnboardingArchiveImport
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _destination = MutableSharedFlow<OnboardingDestination>(replay = 1)
    val destination: SharedFlow<OnboardingDestination> = _destination.asSharedFlow()

    private var finished = false

    fun updateProviderName(value: String) = _state.update { it.copy(providerName = value, saveFailed = false) }

    fun updateApiUrl(value: String) = _state.update { it.copy(apiUrl = value, saveFailed = false) }

    fun updateModel(value: String) = _state.update { it.copy(model = value, saveFailed = false) }

    fun updateApiKey(value: String) = _state.update { it.copy(apiKey = value, saveFailed = false) }

    fun resetToMiMo() {
        _state.update {
            it.copy(
                providerName = MiMoSuggestion.NAME,
                apiUrl = MiMoSuggestion.API_URL,
                model = MiMoSuggestion.MODEL,
                saveFailed = false
            )
        }
    }

    fun setVoiceEnabled(enabled: Boolean) = _state.update { it.copy(voiceEnabled = enabled) }

    fun back() {
        val previous = OnboardingStep.entries.getOrNull(_state.value.step.ordinal - 1) ?: return
        _state.update { it.copy(step = previous, saveFailed = false) }
    }

    fun skip() {
        when (_state.value.step) {
            OnboardingStep.Welcome -> finish(OnboardingDestination.Home, voiceEnabled = false)
            OnboardingStep.Voice -> {
                _state.update { it.copy(voiceEnabled = false, step = OnboardingStep.Explore) }
            }
            OnboardingStep.Explore -> finish(OnboardingDestination.Home, voiceEnabled = _state.value.voiceEnabled)
            else -> advance()
        }
    }

    fun next() {
        when (_state.value.step) {
            OnboardingStep.Welcome -> advance()
            OnboardingStep.Provider -> saveProvider()
            OnboardingStep.Import -> advance()
            OnboardingStep.Voice -> advance()
            OnboardingStep.Explore -> finish(OnboardingDestination.Home, voiceEnabled = _state.value.voiceEnabled)
        }
    }

    fun openBots() = finish(OnboardingDestination.Bots, voiceEnabled = _state.value.voiceEnabled)

    fun openProjects() = finish(OnboardingDestination.Projects, voiceEnabled = _state.value.voiceEnabled)

    fun importZip(uri: Uri) = importArchive { archiveImport.importZip(uri) }

    fun importFolder(uri: Uri) = importArchive { archiveImport.importFolder(uri) }

    internal fun importArchive(load: suspend () -> ProjectArchiveOutcome) {
        if (_state.value.busy) return
        _state.update { it.copy(isImporting = true, importFailure = null) }
        viewModelScope.launch {
            val outcome = try {
                load()
            } catch (_: Exception) {
                ProjectArchiveOutcome.Failure(ProjectArchiveFailure.UNREADABLE)
            }
            _state.update { it.afterArchive(outcome) }
        }
    }

    private fun saveProvider() {
        val current = _state.value
        if (!current.providerReady || current.busy) return
        val apiKey = current.apiKey
        val platform = MiMoSuggestion.platform(
            name = current.providerName,
            apiUrl = current.apiUrl,
            model = current.model,
            apiKey = apiKey
        )
        _state.update { it.copy(isSaving = true, saveFailed = false, apiKey = "") }
        viewModelScope.launch {
            try {
                val url = platform.apiUrl.trimEnd('/')
                val alreadySaved = settingRepository.fetchPlatformV2s().any { existing ->
                    existing.apiUrl.trimEnd('/') == url && existing.model == platform.model
                }
                if (!alreadySaved) {
                    settingRepository.addPlatformV2(platform)
                }
                _state.update { it.copy(isSaving = false, step = OnboardingStep.Import) }
            } catch (_: Exception) {
                _state.update { it.copy(isSaving = false, saveFailed = true, apiKey = apiKey) }
            }
        }
    }

    private fun advance() {
        val next = OnboardingStep.entries.getOrNull(_state.value.step.ordinal + 1) ?: return
        _state.update { it.copy(step = next, saveFailed = false) }
    }

    private fun finish(destination: OnboardingDestination, voiceEnabled: Boolean) {
        if (finished || _state.value.busy) return
        finished = true
        _state.update { it.copy(apiKey = "", voiceEnabled = voiceEnabled) }
        viewModelScope.launch {
            onboardingStore.setVoiceEnabled(voiceEnabled)
            onboardingStore.setCompleted()
            _destination.emit(destination)
        }
    }
}

internal fun OnboardingUiState.afterArchive(outcome: ProjectArchiveOutcome): OnboardingUiState = when (outcome) {
    is ProjectArchiveOutcome.Success -> copy(
        isImporting = false,
        importFailure = null,
        importedProjects = outcome.result.createdProjects + outcome.result.updatedProjects,
        importedChats = outcome.result.chatCount
    )
    is ProjectArchiveOutcome.Failure -> copy(
        isImporting = false,
        importFailure = outcome.failure,
        importedProjects = null
    )
}
