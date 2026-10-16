package dev.chungjungsoo.gptmobile.presentation.ui.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.usage.ModelPrice
import dev.chungjungsoo.gptmobile.data.usage.UsageRepository
import dev.chungjungsoo.gptmobile.data.usage.UsageSummary
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UsageViewModel @Inject constructor(
    private val usageRepository: UsageRepository
) : ViewModel() {
    val summary: StateFlow<UsageSummary> = usageRepository.observeSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageSummary(emptyList(), emptyList(), emptyList(), 0, 0, 0))

    val prices: StateFlow<List<ModelPrice>> = usageRepository.observePrices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _priceError = MutableStateFlow<String?>(null)
    val priceError = _priceError.asStateFlow()

    fun savePrice(modelKey: String, inputUsd: String, outputUsd: String) {
        viewModelScope.launch {
            val saved = usageRepository.savePrice(
                modelKey = modelKey,
                inputUsdPerMillion = inputUsd,
                outputUsdPerMillion = outputUsd,
                updatedAt = System.currentTimeMillis() / 1000
            )
            _priceError.value = if (saved) null else "invalid"
        }
    }

    fun consumePriceError() {
        _priceError.value = null
    }
}
