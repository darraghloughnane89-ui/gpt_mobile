package dev.chungjungsoo.gptmobile.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.gguf.AvailableStorage
import dev.chungjungsoo.gptmobile.data.gguf.DeviceMemory
import dev.chungjungsoo.gptmobile.data.gguf.GgufDownloadDecision
import dev.chungjungsoo.gptmobile.data.gguf.GgufDownloadGate
import dev.chungjungsoo.gptmobile.data.gguf.GgufDownloadInput
import dev.chungjungsoo.gptmobile.data.gguf.GgufDownloadMessages
import dev.chungjungsoo.gptmobile.data.gguf.GgufDownloadRequest
import dev.chungjungsoo.gptmobile.data.gguf.GgufMetadata
import dev.chungjungsoo.gptmobile.data.gguf.GgufModelStore
import dev.chungjungsoo.gptmobile.data.gguf.GgufSha256
import dev.chungjungsoo.gptmobile.data.huggingface.HuggingFaceTokenStore
import dev.chungjungsoo.gptmobile.data.localmodel.DownloadErrorClassifier
import dev.chungjungsoo.gptmobile.data.localmodel.HuggingFaceDownloadAuth
import dev.chungjungsoo.gptmobile.data.localmodel.LocalModelDownloadPaths
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class GgufModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val store: GgufModelStore,
    private val memory: DeviceMemory,
    private val storage: AvailableStorage,
    private val tokenStore: HuggingFaceTokenStore
) : CoroutineWorker(context, params) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notificationId: Int = params.id.hashCode()

    override suspend fun doWork(): Result {
        val request = GgufDownloadInput.from(inputData) ?: return Result.failure()
        ensureNotificationChannel()
        val partial = store.partialFile(request.downloadKey)
        runCatching { setForeground(foreground(percent(partial.length(), request.sizeBytes), request.displayName)) }
        return withContext(Dispatchers.IO) {
            try {
                val decision = GgufDownloadGate.evaluate(
                    fileBytes = request.sizeBytes,
                    availableStorageBytes = storage.availableBytes(store.partialDirectory()),
                    totalMemBytes = memory.totalBytes(),
                    partialBytes = partial.takeIf { it.isFile }?.length() ?: 0L,
                    acceptWarning = true
                )
                if (decision is GgufDownloadDecision.Blocked) {
                    return@withContext failure(decision.message)
                }
                if (store.findByCatalogOrHash(request.catalogId, request.sha256) != null) {
                    partial.delete()
                    return@withContext Result.success()
                }
                download(request, partial)
                if (!request.sha256.isNullOrBlank() && !GgufSha256.matches(partial, request.sha256)) {
                    partial.delete()
                    return@withContext failure(GgufDownloadMessages.CHECKSUM)
                }
                if (!GgufMetadata.isGguf(partial)) {
                    partial.delete()
                    return@withContext failure(GgufDownloadMessages.NOT_GGUF)
                }
                store.adoptFile(
                    displayName = request.displayName,
                    sourceLabel = request.sourceLabel,
                    source = partial,
                    totalMemBytes = memory.totalBytes(),
                    sha256 = request.sha256,
                    licenseName = request.licenseName,
                    catalogId = request.catalogId
                )
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                Log.e(TAG, error.message, error)
                val classification = DownloadErrorClassifier.classify(error, partial.length() > 0L)
                if (DownloadErrorClassifier.shouldRetry(classification, runAttemptCount)) {
                    Result.retry()
                } else {
                    failure(error.message ?: "Download failed")
                }
            } catch (error: Exception) {
                Log.e(TAG, error.message, error)
                partial.delete()
                failure(error.message ?: "Download failed")
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val request = GgufDownloadInput.from(inputData)
        val name = request?.displayName
        val partial = request?.let { store.partialFile(it.downloadKey) }
        val percent = percent(partial?.takeIf { it.isFile }?.length() ?: 0L, request?.sizeBytes ?: 0L)
        return foreground(percent, name)
    }

    private suspend fun download(request: GgufDownloadRequest, partial: File) {
        if (request.sizeBytes > 0L && partial.isFile && partial.length() > request.sizeBytes) {
            partial.delete()
        }
        if (request.sizeBytes > 0L && partial.isFile && partial.length() == request.sizeBytes) {
            publish(partial.length(), request)
            return
        }
        partial.parentFile?.mkdirs()
        val token = runCatching { tokenStore.readAccessToken() }.getOrNull()
        val existing = partial.takeIf { it.isFile }?.length() ?: 0L
        publish(existing, request)
        val connection = HuggingFaceDownloadAuth.openConnection(
            url = request.url,
            accessToken = token,
            extraHeaders = LocalModelDownloadPaths.resumeHeaders(existing),
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS
        )
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("HTTP error code: $code")
            }
            val contentRange = connection.getHeaderField("Content-Range")
            val append = LocalModelDownloadPaths.shouldAppendToPartial(existing, contentRange)
            var downloaded = if (append) {
                LocalModelDownloadPaths.downloadedBytesAfterConnect(existing, contentRange)
            } else {
                0L
            }
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var lastPublish = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (isStopped) throw CancellationException("GGUF download cancelled")
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastPublish > PROGRESS_INTERVAL_MS) {
                            publish(downloaded, request)
                            lastPublish = now
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (request.sizeBytes > 0L && partial.length() != request.sizeBytes) {
            throw IOException("Incomplete GGUF download")
        }
        publish(partial.length(), request)
    }

    private suspend fun publish(downloaded: Long, request: GgufDownloadRequest) {
        setProgress(
            Data.Builder()
                .putLong(GgufDownloadInput.KEY_RECEIVED_BYTES, downloaded)
                .putLong(GgufDownloadInput.KEY_TOTAL_BYTES, request.sizeBytes)
                .build()
        )
        runCatching { setForeground(foreground(percent(downloaded, request.sizeBytes), request.displayName)) }
    }

    private fun failure(message: String): Result = Result.failure(
        Data.Builder().putString(GgufDownloadInput.KEY_ERROR_MESSAGE, message).build()
    )

    private fun ensureNotificationChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.gguf_download_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.gguf_download_notification_channel_description)
            }
        )
    }

    private fun foreground(progress: Int, modelName: String?): ForegroundInfo {
        val title = applicationContext.getString(R.string.gguf_download_notification_title)
        val content = if (modelName.isNullOrBlank()) {
            applicationContext.getString(R.string.gguf_download_notification_progress, progress)
        } else {
            applicationContext.getString(R.string.gguf_download_notification_content, modelName, progress)
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_gpt_mobile_monochrome_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, progress, progress <= 0)
            .setContentIntent(pendingIntent)
            .build()
        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun percent(downloaded: Long, total: Long): Int = if (total <= 0L) {
        0
    } else {
        ((downloaded * 100) / total).toInt().coerceIn(0, 100)
    }

    private companion object {
        const val TAG = "GgufModelDownload"
        const val CHANNEL_ID = "gguf_model_downloads"
        const val PROGRESS_INTERVAL_MS = 200L
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
