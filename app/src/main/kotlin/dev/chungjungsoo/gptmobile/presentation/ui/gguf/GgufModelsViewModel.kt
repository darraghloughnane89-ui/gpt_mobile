package dev.chungjungsoo.gptmobile.presentation.ui.gguf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.gguf.DeviceMemory
import dev.chungjungsoo.gptmobile.data.gguf.GgufDownloadSnapshot
import dev.chungjungsoo.gptmobile.data.gguf.GgufDownloadState
import dev.chungjungsoo.gptmobile.data.gguf.GgufModelCoordinator
import dev.chungjungsoo.gptmobile.data.gguf.GgufModelRecord
import dev.chungjungsoo.gptmobile.data.gguf.GgufModelStore
import dev.chungjungsoo.gptmobile.data.gguf.GgufPendingDownload
import dev.chungjungsoo.gptmobile.data.gguf.GgufRamPolicy
import dev.chungjungsoo.gptmobile.data.gguf.GgufStartResult
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GgufModelsUiState(
    val models: List<GgufModelRecord> = emptyList(),
    val isLoading: Boolean = true,
    val downloads: List<GgufDownloadSnapshot> = emptyList(),
    val partialBytes: Map<String, Long> = emptyMap(),
    val defaultModelId: String? = null,
    val pendingConfirmation: GgufPendingDownload? = null,
    val message: String? = null,
    val editing: GgufModelRecord? = null
)

@HiltViewModel
class GgufModelsViewModel @Inject constructor(
    private val store: GgufModelStore,
    private val memory: DeviceMemory,
    private val coordinator: GgufModelCoordinator
) : ViewModel() {
    private val _uiState = MutableStateFlow(GgufModelsUiState())
    val uiState: StateFlow<GgufModelsUiState> = _uiState.asStateFlow()
    private val seenSuccess = mutableSetOf<String>()
    private var reportedFailure: String? = null

    init {
        viewModelScope.launch {
            coordinator.pending.collect { pending ->
                _uiState.update { it.copy(pendingConfirmation = pending) }
            }
        }
        viewModelScope.launch {
            coordinator.downloads.collect { downloads ->
                _uiState.update { it.copy(downloads = downloads) }
                val failure = downloads.firstOrNull { it.state == GgufDownloadState.FAILED }?.errorMessage
                if (!failure.isNullOrBlank() && failure != reportedFailure) {
                    reportedFailure = failure
                    _uiState.update { it.copy(message = failure) }
                }
                val finished = downloads.filter { it.state == GgufDownloadState.SUCCEEDED }.map { it.downloadKey }
                if (finished.any { it !in seenSuccess }) {
                    seenSuccess += finished
                    refresh()
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val models = runCatching { store.list() }.getOrDefault(emptyList())
            val defaultId = runCatching { coordinator.defaultModelId() }.getOrNull()
            val partials = runCatching { store.partialSizes() }.getOrDefault(emptyMap())
            _uiState.update {
                it.copy(
                    isLoading = false,
                    models = models,
                    defaultModelId = defaultId?.takeIf { id -> models.any { model -> model.id == id } },
                    partialBytes = partials
                )
            }
        }
    }

    fun importUri(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val name = displayName(context, uri)
                val sizeHint = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    descriptor.length.takeIf { it > 0L } ?: 0L
                } ?: 0L
                val stream = context.contentResolver.openInputStream(uri) ?: error("Could not open that file")
                store.importStream(
                    displayName = name.removeSuffix(".gguf"),
                    sourceLabel = name,
                    input = stream,
                    sizeHint = sizeHint,
                    totalMemBytes = memory.totalBytes()
                )
            }
            publish(result.exceptionOrNull())
            if (result.isSuccess) refresh()
        }
    }

    fun downloadCatalog(catalogId: String) {
        viewModelScope.launch {
            publishResult(coordinator.requestCatalogDownload(catalogId))
        }
    }

    fun downloadUrl(url: String) {
        viewModelScope.launch {
            publishResult(coordinator.requestPastedUrl(url))
        }
    }

    fun confirmDownload() {
        viewModelScope.launch {
            publishResult(coordinator.confirmPending())
        }
    }

    fun dismissDownloadConfirmation() {
        coordinator.dismissPending()
    }

    fun cancelDownload(downloadKey: String) {
        coordinator.cancelDownload(downloadKey)
    }

    fun discardPartial(downloadKey: String) {
        coordinator.discardPartial(downloadKey)
        refresh()
    }

    fun setDefault(modelId: String) {
        viewModelScope.launch {
            val result = runCatching { coordinator.setDefault(modelId) }
            publish(result.exceptionOrNull())
            if (result.isSuccess) refresh()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            coordinator.deleteInstalled(id)
            refresh()
        }
    }

    fun beginEdit(record: GgufModelRecord) {
        _uiState.update { it.copy(editing = record) }
    }

    fun dismissEdit() {
        _uiState.update { it.copy(editing = null) }
    }

    fun saveParameters(contextSize: Int, temperature: Float) {
        val editing = _uiState.value.editing ?: return
        viewModelScope.launch {
            store.updateParameters(editing.id, contextSize, temperature, memory.totalBytes())
            _uiState.update { it.copy(editing = null) }
            refresh()
        }
    }

    fun recommendedContext(): Int = GgufRamPolicy.recommendedContext(memory.totalBytes())

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun publishResult(result: GgufStartResult) {
        when (result) {
            GgufStartResult.Started, GgufStartResult.AlreadyRunning -> Unit
            is GgufStartResult.NeedsConfirmation -> Unit
            is GgufStartResult.Rejected -> _uiState.update { it.copy(message = result.message) }
        }
    }

    private fun displayName(context: android.content.Context, uri: Uri): String = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment ?: "model.gguf"

    private fun publish(error: Throwable?) {
        if (error is CancellationException) return
        _uiState.update { it.copy(message = error?.message) }
    }
}
