package dev.chungjungsoo.gptmobile.presentation.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.diagnostics.DiagnosticsStore
import dev.chungjungsoo.gptmobile.data.diagnostics.StoredCrash
import dev.chungjungsoo.gptmobile.data.diagnostics.StoredProviderError
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val crashes: List<StoredCrash> = emptyList(),
    val providerErrors: List<StoredProviderError> = emptyList()
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val diagnosticsStore: DiagnosticsStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = DiagnosticsUiState(
                crashes = diagnosticsStore.listCrashes(),
                providerErrors = diagnosticsStore.listProviderErrors()
            )
        }
    }

    fun redactedShareReport(): String = diagnosticsStore.buildShareReport()
}
