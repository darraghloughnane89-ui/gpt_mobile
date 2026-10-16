package dev.chungjungsoo.gptmobile.presentation.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.onboarding.OnboardingStore
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.presentation.StartupRecoveryGate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val onboardingStore: OnboardingStore
) : ViewModel() {

    sealed class SplashEvent {
        data object OpenIntro : SplashEvent()
        data object OpenHome : SplashEvent()
        data object OpenMigrate : SplashEvent()
    }

    private val _isReady: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _event: MutableSharedFlow<SplashEvent> = MutableSharedFlow(replay = 1)
    val event: SharedFlow<SplashEvent> = _event.asSharedFlow()

    init {
        viewModelScope.launch {
            StartupRecoveryGate.await()
            val platforms = settingRepository.fetchPlatforms()
            val platformV2s = settingRepository.fetchPlatformV2s()
            when (
                resolveStartupRoute(
                    legacyPlatforms = platforms,
                    platformV2s = platformV2s,
                    onboardingCompleted = onboardingStore.isCompleted()
                )
            ) {
                StartupRoute.INTRO -> sendSplashEvent(SplashEvent.OpenIntro)
                StartupRoute.MIGRATE -> sendSplashEvent(SplashEvent.OpenMigrate)
                StartupRoute.HOME -> sendSplashEvent(SplashEvent.OpenHome)
            }

            setAsReady()
        }
    }

    private suspend fun sendSplashEvent(event: SplashEvent) {
        _event.emit(event)
    }

    private fun setAsReady() {
        _isReady.update { true }
    }
}
