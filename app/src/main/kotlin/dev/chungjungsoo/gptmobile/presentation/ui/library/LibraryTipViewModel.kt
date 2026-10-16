package dev.chungjungsoo.gptmobile.presentation.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.onboarding.OnboardingStore
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object TipIds {
    const val CHATS = "chats"
    const val PROJECTS = "projects"
    const val BOTS = "bots"
    const val SKILLS = "skills"
    const val MEMORY = "memory"
}

@HiltViewModel
class LibraryTipViewModel @Inject constructor(
    private val onboardingStore: OnboardingStore
) : ViewModel() {
    private val _dismissed = MutableStateFlow<Set<String>>(emptySet())
    val dismissed: StateFlow<Set<String>> = _dismissed.asStateFlow()

    init {
        viewModelScope.launch {
            _dismissed.value = onboardingStore.dismissedTips()
        }
    }

    fun dismiss(id: String) {
        viewModelScope.launch {
            onboardingStore.dismissTip(id)
            _dismissed.value = onboardingStore.dismissedTips()
        }
    }
}
