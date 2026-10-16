package dev.chungjungsoo.gptmobile.presentation.ui.gguf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.gguf.DeviceMemory
import dev.chungjungsoo.gptmobile.data.gguf.GgufDownloader
import dev.chungjungsoo.gptmobile.data.gguf.GgufModelRecord
import dev.chungjungsoo.gptmobile.data.gguf.GgufModelStore
import dev.chungjungsoo.gptmobile.data.gguf.GgufRamPolicy
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GgufModelsUiState(
    val models: List<GgufModelRecord> = emptyList(),
    val isLoading: Boolean = true,
    val downloadFraction: Float? = null,
    val downloadLabel: String? = null,
    val message: String? = null,
    val editing: GgufModelRecord? = null
)

@HiltViewModel
class GgufModelsViewModel @Inject constructor(
    private val store: GgufModelStore,
    private val memory: DeviceMemory,
    networkClient: NetworkClient
) : ViewModel() {
    private val downloader = GgufDownloader(networkClient())
    private val _uiState = MutableStateFlow(GgufModelsUiState())
    val uiState: StateFlow<GgufModelsUiState> = _uiState.asStateFlow()
    private var downloadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val models = runCatching { store.list() }.getOrDefault(emptyList())
            _uiState.update { it.copy(isLoading = false, models = models) }
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

    fun download(url: String, cacheDir: File) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            _uiState.update { it.copy(message = "Enter an http or https URL") }
            return
        }
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val name = trimmed.substringAfterLast('/').substringBefore('?').ifBlank { "model.gguf" }
            val safe = name.filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }.ifBlank { "model.gguf" }
            val temp = File(cacheDir, "gguf-download-$safe")
            _uiState.update { it.copy(downloadFraction = 0f, downloadLabel = safe, message = null) }
            val downloaded = runCatching {
                downloader.download(trimmed, temp) { read, total ->
                    val fraction = if (total != null && total > 0L) read.toFloat() / total.toFloat() else 0f
                    _uiState.update { state -> state.copy(downloadFraction = fraction.coerceIn(0f, 1f)) }
                }
                store.importStream(
                    displayName = safe.removeSuffix(".gguf"),
                    sourceLabel = trimmed,
                    input = temp.inputStream(),
                    sizeHint = temp.length(),
                    totalMemBytes = memory.totalBytes()
                )
            }
            temp.delete()
            publish(downloaded.exceptionOrNull())
            _uiState.update { it.copy(downloadFraction = null, downloadLabel = null) }
            if (downloaded.isSuccess) refresh()
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.update { it.copy(downloadFraction = null, downloadLabel = null, message = null) }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            store.delete(id)
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

    private fun displayName(context: android.content.Context, uri: Uri): String = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    } ?: uri.lastPathSegment ?: "model.gguf"

    private fun publish(error: Throwable?) {
        if (error is CancellationException) return
        _uiState.update { it.copy(message = error?.message) }
    }

    override fun onCleared() {
        downloadJob?.cancel()
        super.onCleared()
    }
}
