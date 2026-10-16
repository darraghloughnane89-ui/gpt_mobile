package dev.chungjungsoo.gptmobile.presentation.ui.privacy

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.privacy.AppLockPolicy
import dev.chungjungsoo.gptmobile.data.privacy.PrivacySettingsRepository
import dev.chungjungsoo.gptmobile.data.security.VaultSession
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppLockUiState(
    val resolved: Boolean = false,
    val enabled: Boolean = false,
    val timeoutSeconds: Int = 0,
    val flagSecure: Boolean = false,
    val locked: Boolean = false,
    val promptRequested: Boolean = false,
    val vaultPromptRequested: Boolean = false,
    val deviceLockAvailable: Boolean = true
)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val privacySettings: PrivacySettingsRepository,
    private val vaultSession: VaultSession
) : ViewModel() {
    private val _state = MutableStateFlow(AppLockUiState(deviceLockAvailable = AppLockAuthenticator.canAuthenticate(context)))
    val state = _state.asStateFlow()
    private var appliedInitialLock = false
    private var backgroundedAtMillis: Long? = null

    init {
        viewModelScope.launch {
            privacySettings.appLockEnabled.collect { enabled ->
                if (!enabled && !_state.value.deviceLockAvailable) {
                    vaultSession.unlock()
                }
                _state.update { current ->
                    val lockNow = !appliedInitialLock && enabled
                    appliedInitialLock = true
                    val askForVault = !enabled &&
                        current.deviceLockAvailable &&
                        !vaultSession.isUnlocked() &&
                        !current.vaultPromptRequested
                    current.copy(
                        resolved = true,
                        enabled = enabled,
                        locked = if (!enabled) false else if (lockNow) true else current.locked,
                        promptRequested = if (lockNow) true else if (!enabled) false else current.promptRequested,
                        vaultPromptRequested = if (askForVault) true else current.vaultPromptRequested
                    )
                }
            }
        }
        viewModelScope.launch {
            privacySettings.lockTimeoutSeconds.collect { seconds ->
                _state.update { it.copy(timeoutSeconds = seconds) }
            }
        }
        viewModelScope.launch {
            privacySettings.flagSecure.collect { enabled ->
                _state.update { it.copy(flagSecure = enabled) }
            }
        }
    }

    fun onEnteredBackground(nowMillis: Long) {
        backgroundedAtMillis = nowMillis
    }

    fun onEnteredForeground(nowMillis: Long) {
        val backgroundedAt = backgroundedAtMillis ?: return
        backgroundedAtMillis = null
        val current = _state.value
        if (AppLockPolicy.shouldLock(current.enabled, current.timeoutSeconds, backgroundedAt, nowMillis)) {
            vaultSession.lock()
            _state.update { it.copy(locked = true, promptRequested = true) }
        } else if (!current.enabled && !vaultSession.isUnlocked()) {
            _state.update { it.copy(vaultPromptRequested = current.deviceLockAvailable) }
        }
    }

    fun requestPrompt() {
        _state.update { it.copy(promptRequested = true, locked = true) }
    }

    fun onAuthenticationResult(success: Boolean) {
        if (success) vaultSession.unlock() else vaultSession.lock()
        _state.update {
            if (success) {
                it.copy(locked = false, promptRequested = false)
            } else {
                it.copy(locked = true, promptRequested = false)
            }
        }
    }

    fun onVaultAuthenticationResult(success: Boolean) {
        if (success) vaultSession.unlock()
        _state.update { it.copy(vaultPromptRequested = false) }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { privacySettings.setAppLockEnabled(enabled) }
    }

    fun setTimeoutSeconds(seconds: Int) {
        viewModelScope.launch { privacySettings.setLockTimeoutSeconds(seconds) }
    }

    fun setFlagSecure(enabled: Boolean) {
        viewModelScope.launch { privacySettings.setFlagSecure(enabled) }
    }

    fun disableBecauseUnavailable() {
        viewModelScope.launch {
            privacySettings.setAppLockEnabled(false)
            _state.update { it.copy(locked = false, promptRequested = false, enabled = false) }
        }
    }
}
