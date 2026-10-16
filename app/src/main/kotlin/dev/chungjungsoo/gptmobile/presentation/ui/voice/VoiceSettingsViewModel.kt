package dev.chungjungsoo.gptmobile.presentation.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.voice.MiMoSpeechModels
import dev.chungjungsoo.gptmobile.data.voice.VoiceBackendStatus
import dev.chungjungsoo.gptmobile.data.voice.VoiceSpeaker
import dev.chungjungsoo.gptmobile.data.voice.VoiceSpeechKeyStore
import dev.chungjungsoo.gptmobile.data.voice.VoiceSpeechPreferences
import dev.chungjungsoo.gptmobile.data.voice.VoiceSpeechSettings
import dev.chungjungsoo.gptmobile.data.voice.voiceBackendStatus
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceSettingsUiState(
    val useMiMo: Boolean = false,
    val voiceId: String = MiMoSpeechModels.DEFAULT_VOICE,
    val hasKey: Boolean = false,
    val keyPreview: String = "",
    val status: VoiceBackendStatus = VoiceBackendStatus.DEVICE_BY_CHOICE,
    val playing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val preferences: VoiceSpeechPreferences,
    private val keys: VoiceSpeechKeyStore,
    private val speaker: VoiceSpeaker
) : ViewModel() {
    private val _state = MutableStateFlow(VoiceSettingsUiState())
    val state: StateFlow<VoiceSettingsUiState> = _state.asStateFlow()
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.settings.collect { settings ->
                publish(settings)
            }
        }
    }

    fun setUseMiMo(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setUseMiMo(enabled)
        }
    }

    fun setVoice(voiceId: String) {
        viewModelScope.launch {
            preferences.setVoiceId(voiceId)
        }
    }

    fun saveKey(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                keys.saveKey(trimmed)
                publish(preferences.settings.first())
                _state.update { it.copy(error = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "The MiMo key could not be saved.") }
            }
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            keys.clearKey()
            publish(preferences.settings.first())
        }
    }

    fun preview(sample: String) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _state.update { it.copy(playing = true, error = null) }
            try {
                speaker.speak("voice-preview", sample)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "Speech failed.") }
            } finally {
                _state.update { it.copy(playing = false) }
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        viewModelScope.launch { speaker.stop() }
    }

    private suspend fun publish(settings: VoiceSpeechSettings) {
        val hasKey = keys.hasKey()
        _state.update { current ->
            current.copy(
                useMiMo = settings.useMiMo,
                voiceId = settings.voiceId,
                hasKey = hasKey,
                keyPreview = keys.preview(),
                status = voiceBackendStatus(settings.useMiMo, hasKey)
            )
        }
    }
}
