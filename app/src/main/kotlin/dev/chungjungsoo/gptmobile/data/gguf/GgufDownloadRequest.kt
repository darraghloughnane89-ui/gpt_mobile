package dev.chungjungsoo.gptmobile.data.gguf

import androidx.work.Data
import java.io.File
import kotlinx.coroutines.flow.Flow

data class GgufDownloadRequest(
    val downloadKey: String,
    val displayName: String,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String?,
    val licenseName: String?,
    val catalogId: String?,
    val sourceLabel: String
)

enum class GgufDownloadState {
    ENQUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

data class GgufDownloadSnapshot(
    val downloadKey: String,
    val state: GgufDownloadState,
    val receivedBytes: Long,
    val totalBytes: Long,
    val errorMessage: String?
)

data class GgufPendingDownload(
    val request: GgufDownloadRequest,
    val warning: String
)

sealed class GgufStartResult {
    data object Started : GgufStartResult()
    data object AlreadyRunning : GgufStartResult()
    data class NeedsConfirmation(val pending: GgufPendingDownload) : GgufStartResult()
    data class Rejected(val message: String) : GgufStartResult()
}

interface GgufDownloadScheduler {
    fun enqueue(request: GgufDownloadRequest)
    fun cancel(downloadKey: String)
    fun isActive(downloadKey: String): Boolean
    fun observe(): Flow<List<GgufDownloadSnapshot>>
}

fun interface AvailableStorage {
    fun availableBytes(directory: File): Long
}

interface GgufRemoteMetadataSource {
    suspend fun lookup(url: String): GgufPublishedMetadata
}

interface GgufDefaults {
    suspend fun getDefaultModelId(): String?
    suspend fun setDefaultModelId(id: String?)
}

object GgufDownloadInput {
    const val WORK_TAG = "gguf_model_download"
    const val ID_TAG_PREFIX = "gguf_id:"
    const val KEY_DOWNLOAD_KEY = "download_key"
    const val KEY_DISPLAY_NAME = "display_name"
    const val KEY_URL = "download_url"
    const val KEY_FILE_NAME = "file_name"
    const val KEY_TOTAL_BYTES = "total_bytes"
    const val KEY_SHA256 = "sha256"
    const val KEY_LICENSE = "license_name"
    const val KEY_CATALOG_ID = "catalog_id"
    const val KEY_SOURCE_LABEL = "source_label"
    const val KEY_RECEIVED_BYTES = "received_bytes"
    const val KEY_ERROR_MESSAGE = "error_message"
    const val INITIAL_BACKOFF_SECONDS = 10L

    fun idTag(downloadKey: String): String = "$ID_TAG_PREFIX$downloadKey"

    fun uniqueWorkName(downloadKey: String): String = "gguf_download_$downloadKey"

    fun downloadKeyFromTag(tag: String): String? = tag
        .takeIf { it.startsWith(ID_TAG_PREFIX) }
        ?.removePrefix(ID_TAG_PREFIX)
        ?.takeIf { GgufPartialFiles.isSafeKey(it) }

    fun toData(request: GgufDownloadRequest): Data = Data.Builder()
        .putString(KEY_DOWNLOAD_KEY, request.downloadKey)
        .putString(KEY_DISPLAY_NAME, request.displayName)
        .putString(KEY_URL, request.url)
        .putString(KEY_FILE_NAME, request.fileName)
        .putLong(KEY_TOTAL_BYTES, request.sizeBytes)
        .putString(KEY_SHA256, request.sha256.orEmpty())
        .putString(KEY_LICENSE, request.licenseName.orEmpty())
        .putString(KEY_CATALOG_ID, request.catalogId.orEmpty())
        .putString(KEY_SOURCE_LABEL, request.sourceLabel)
        .build()

    fun from(data: Data): GgufDownloadRequest? {
        val key = data.getString(KEY_DOWNLOAD_KEY) ?: return null
        val url = data.getString(KEY_URL) ?: return null
        val fileName = data.getString(KEY_FILE_NAME) ?: return null
        if (!GgufPartialFiles.isSafeKey(key)) return null
        if (!url.startsWith("https://")) return null
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return null
        val size = data.getLong(KEY_TOTAL_BYTES, 0L)
        if (size <= 0L) return null
        val catalogId = data.getString(KEY_CATALOG_ID)?.takeIf { it.isNotBlank() }
        return GgufDownloadRequest(
            downloadKey = key,
            displayName = data.getString(KEY_DISPLAY_NAME)?.takeIf { it.isNotBlank() } ?: fileName,
            url = url,
            fileName = fileName,
            sizeBytes = size,
            sha256 = data.getString(KEY_SHA256)?.takeIf { it.isNotBlank() },
            licenseName = data.getString(KEY_LICENSE)?.takeIf { it.isNotBlank() },
            catalogId = catalogId,
            sourceLabel = data.getString(KEY_SOURCE_LABEL)?.takeIf { it.isNotBlank() } ?: url
        )
    }
}
