package dev.chungjungsoo.gptmobile.presentation.ui.voice

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.voice.VoiceCredentialLookup
import dev.chungjungsoo.gptmobile.data.voice.VoiceEngine
import dev.chungjungsoo.gptmobile.data.voice.VoicePreferences
import dev.chungjungsoo.gptmobile.data.voice.VoiceSettings
import dev.chungjungsoo.gptmobile.presentation.voice.AssistantRoleGateway
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val preferences: VoicePreferences,
    private val credentials: VoiceCredentialLookup,
    private val assistantRole: AssistantRoleGateway
) : ViewModel() {
    val settings = preferences.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        VoiceSettings()
    )
    private val _openAiAvailable = MutableStateFlow(false)
    val openAiAvailable = _openAiAvailable.asStateFlow()
    private val _assistantAvailable = MutableStateFlow(false)
    val assistantAvailable = _assistantAvailable.asStateFlow()
    private val _assistantHeld = MutableStateFlow(false)
    val assistantHeld = _assistantHeld.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _openAiAvailable.value = credentials.openAiCredential() != null
            _assistantAvailable.value = assistantRole.isAvailable()
            _assistantHeld.value = assistantRole.isHeld()
        }
    }

    fun setSpeechRecognition(engine: VoiceEngine) {
        viewModelScope.launch { preferences.setSpeechRecognition(engine) }
    }

    fun setSpeechSynthesis(engine: VoiceEngine) {
        viewModelScope.launch { preferences.setSpeechSynthesis(engine) }
    }

    fun assistantRequestIntent(): Intent? = assistantRole.requestIntent()
}
