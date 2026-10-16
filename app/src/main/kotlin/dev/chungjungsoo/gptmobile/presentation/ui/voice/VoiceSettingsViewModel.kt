package dev.chungjungsoo.gptmobile.presentation.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.voice.HandsFreePreferences
import dev.chungjungsoo.gptmobile.data.voice.HandsFreeSettings
import dev.chungjungsoo.gptmobile.data.voice.WakeEngineLicence
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val preferences: HandsFreePreferences
) : ViewModel() {
    val settings: StateFlow<HandsFreeSettings> = preferences.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HandsFreeSettings()
    )
    val licence: WakeEngineLicence = WakeEngineLicence.OPEN_WAKE_WORD

    fun setWakeEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setWakeEnabled(enabled) }
    }
}
