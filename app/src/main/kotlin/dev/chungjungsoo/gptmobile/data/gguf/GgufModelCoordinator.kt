package dev.chungjungsoo.gptmobile.data.gguf

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class GgufModelCoordinator @Inject constructor(
    private val store: GgufModelStore,
    private val defaults: GgufDefaults,
    private val memory: DeviceMemory,
    private val storage: AvailableStorage,
    private val scheduler: GgufDownloadScheduler,
    private val metadata: GgufRemoteMetadataSource
) {
    private val _pending = MutableStateFlow<GgufPendingDownload?>(null)
    val pending: StateFlow<GgufPendingDownload?> = _pending.asStateFlow()
    val downloads: Flow<List<GgufDownloadSnapshot>> = scheduler.observe()

    suspend fun requestCatalogDownload(catalogId: String): GgufStartResult = withContext(Dispatchers.IO) {
        val entry = GgufCatalog.find(catalogId)
            ?: return@withContext GgufStartResult.Rejected("That model is not in the suggested list.")
        if (store.findByCatalogOrHash(entry.id, entry.sha256) != null) {
            return@withContext GgufStartResult.Rejected(GgufDownloadMessages.ALREADY_INSTALLED)
        }
        start(entry.toDownloadRequest())
    }

    suspend fun requestPastedUrl(rawUrl: String): GgufStartResult = withContext(Dispatchers.IO) {
        val link = HuggingFaceGgufUrl.parse(rawUrl)
            ?: return@withContext GgufStartResult.Rejected(GgufDownloadMessages.NOT_A_GGUF_URL)
        val published = metadata.lookup(link.downloadUrl)
        val size = published.sizeBytes
        if (size == null || size <= 0L) {
            return@withContext GgufStartResult.Rejected(GgufDownloadMessages.SIZE_UNPUBLISHED)
        }
        val sha256 = published.sha256?.takeIf { it.length == 64 }
        if (store.findByCatalogOrHash(catalogId = null, sha256 = sha256) != null) {
            return@withContext GgufStartResult.Rejected(GgufDownloadMessages.ALREADY_INSTALLED)
        }
        start(
            GgufDownloadRequest(
                downloadKey = GgufPartialFiles.keyForUrl(link.downloadUrl),
                displayName = link.fileName.removeSuffix(".gguf").removeSuffix(".GGUF"),
                url = link.downloadUrl,
                fileName = link.fileName,
                sizeBytes = size,
                sha256 = sha256,
                licenseName = null,
                catalogId = null,
                sourceLabel = link.downloadUrl
            )
        )
    }

    suspend fun confirmPending(): GgufStartResult = withContext(Dispatchers.IO) {
        val pendingDownload = _pending.value ?: return@withContext GgufStartResult.Rejected("Nothing to download.")
        _pending.value = null
        val decision = decide(pendingDownload.request, acceptWarning = true)
        when (decision) {
            GgufDownloadDecision.Allowed -> {
                scheduler.enqueue(pendingDownload.request)
                GgufStartResult.Started
            }

            is GgufDownloadDecision.Blocked -> GgufStartResult.Rejected(decision.message)
            is GgufDownloadDecision.Confirm -> GgufStartResult.Rejected(decision.warning)
        }
    }

    fun dismissPending() {
        _pending.value = null
    }

    fun cancelDownload(downloadKey: String) {
        if (!GgufPartialFiles.isSafeKey(downloadKey)) return
        scheduler.cancel(downloadKey)
    }

    fun discardPartial(downloadKey: String) {
        if (!GgufPartialFiles.isSafeKey(downloadKey)) return
        scheduler.cancel(downloadKey)
        store.partialFile(downloadKey).delete()
    }

    suspend fun setDefault(modelId: String) = withContext(Dispatchers.IO) {
        if (store.get(modelId) == null) error(GgufDownloadMessages.NOT_ON_DEVICE)
        defaults.setDefaultModelId(modelId)
    }

    suspend fun defaultModelId(): String? = defaults.getDefaultModelId()

    suspend fun deleteInstalled(modelId: String) = withContext(Dispatchers.IO) {
        val defaultId = defaults.getDefaultModelId()
        store.delete(modelId)
        if (defaultId == modelId) defaults.setDefaultModelId(null)
    }

    private fun start(request: GgufDownloadRequest): GgufStartResult {
        if (!GgufPartialFiles.isSafeKey(request.downloadKey)) {
            return GgufStartResult.Rejected("That download could not be started.")
        }
        if (scheduler.isActive(request.downloadKey)) {
            return GgufStartResult.AlreadyRunning
        }
        return when (val decision = decide(request, acceptWarning = false)) {
            GgufDownloadDecision.Allowed -> {
                scheduler.enqueue(request)
                GgufStartResult.Started
            }

            is GgufDownloadDecision.Confirm -> {
                val pendingDownload = GgufPendingDownload(request, decision.warning)
                _pending.value = pendingDownload
                GgufStartResult.NeedsConfirmation(pendingDownload)
            }

            is GgufDownloadDecision.Blocked -> GgufStartResult.Rejected(decision.message)
        }
    }

    private fun decide(request: GgufDownloadRequest, acceptWarning: Boolean): GgufDownloadDecision {
        val partial = store.partialFile(request.downloadKey).takeIf { it.isFile }?.length() ?: 0L
        return GgufDownloadGate.evaluate(
            fileBytes = request.sizeBytes,
            availableStorageBytes = storage.availableBytes(store.partialDirectory()),
            totalMemBytes = memory.totalBytes(),
            partialBytes = partial,
            acceptWarning = acceptWarning
        )
    }
}
