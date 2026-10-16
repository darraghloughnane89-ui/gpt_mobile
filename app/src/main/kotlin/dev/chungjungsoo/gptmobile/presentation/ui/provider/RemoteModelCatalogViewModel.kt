package dev.chungjungsoo.gptmobile.presentation.ui.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.supportsRemoteModelList
import dev.chungjungsoo.gptmobile.data.provider.ModelListRepository
import dev.chungjungsoo.gptmobile.data.provider.RemoteModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteCatalogState(
    val models: List<RemoteModel> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class RemoteModelCatalogViewModel @Inject constructor(
    private val repository: ModelListRepository
) : ViewModel() {
    private val _states = MutableStateFlow<Map<String, RemoteCatalogState>>(emptyMap())
    val states: StateFlow<Map<String, RemoteCatalogState>> = _states.asStateFlow()

    fun key(clientType: ClientType, apiUrl: String): String = "${clientType.name}|$apiUrl"

    fun defaults(clientType: ClientType): List<RemoteModel> = repository.defaults(clientType)

    fun ensureLoaded(clientType: ClientType, apiUrl: String, token: String?) {
        if (!clientType.supportsRemoteModelList()) return
        val cacheKey = key(clientType, apiUrl)
        val existing = _states.value[cacheKey]
        if (existing != null && (existing.models.isNotEmpty() || existing.isLoading)) return
        val cached = repository.cached(clientType, apiUrl)
        _states.update { it + (cacheKey to RemoteCatalogState(models = cached)) }
        if (repository.cached(clientType, apiUrl) == repository.defaults(clientType)) {
            refresh(clientType, apiUrl, token)
        }
    }

    fun refresh(clientType: ClientType, apiUrl: String, token: String?) {
        if (!clientType.supportsRemoteModelList()) return
        val cacheKey = key(clientType, apiUrl)
        _states.update { current ->
            val previous = current[cacheKey] ?: RemoteCatalogState(models = repository.defaults(clientType))
            current + (cacheKey to previous.copy(isLoading = true, message = null))
        }
        viewModelScope.launch {
            val result = runCatching { repository.refresh(clientType, apiUrl, token) }
            _states.update { current ->
                val previous = current[cacheKey] ?: RemoteCatalogState()
                current + (
                    cacheKey to if (result.isSuccess) {
                        previous.copy(models = result.getOrThrow(), isLoading = false, message = null)
                    } else {
                        previous.copy(
                            isLoading = false,
                            message = result.exceptionOrNull()?.message ?: "Could not load models"
                        )
                    }
                    )
            }
        }
    }
}
