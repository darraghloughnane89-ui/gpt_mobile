package dev.chungjungsoo.gptmobile.data.gguf

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.worker.GgufModelDownloadWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class WorkManagerGgufDownloadScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context
) : GgufDownloadScheduler {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override fun enqueue(request: GgufDownloadRequest) {
        val work = OneTimeWorkRequestBuilder<GgufModelDownloadWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                GgufDownloadInput.INITIAL_BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .setInputData(GgufDownloadInput.toData(request))
            .addTag(GgufDownloadInput.WORK_TAG)
            .addTag(GgufDownloadInput.idTag(request.downloadKey))
            .build()
        workManager.enqueueUniqueWork(
            GgufDownloadInput.uniqueWorkName(request.downloadKey),
            ExistingWorkPolicy.KEEP,
            work
        )
    }

    override fun cancel(downloadKey: String) {
        workManager.cancelUniqueWork(GgufDownloadInput.uniqueWorkName(downloadKey))
    }

    override fun isActive(downloadKey: String): Boolean {
        val infos = workManager.getWorkInfosForUniqueWork(GgufDownloadInput.uniqueWorkName(downloadKey)).get()
        return infos.any { info ->
            info.state == WorkInfo.State.ENQUEUED ||
                info.state == WorkInfo.State.RUNNING ||
                info.state == WorkInfo.State.BLOCKED
        }
    }

    override fun observe(): Flow<List<GgufDownloadSnapshot>> = workManager
        .getWorkInfosByTagFlow(GgufDownloadInput.WORK_TAG)
        .map { infos -> infos.mapNotNull { it.toSnapshot() } }

    private fun WorkInfo.toSnapshot(): GgufDownloadSnapshot? {
        val key = tags.firstNotNullOfOrNull(GgufDownloadInput::downloadKeyFromTag) ?: return null
        val state = when (this.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> GgufDownloadState.ENQUEUED
            WorkInfo.State.RUNNING -> GgufDownloadState.RUNNING
            WorkInfo.State.SUCCEEDED -> GgufDownloadState.SUCCEEDED
            WorkInfo.State.FAILED -> GgufDownloadState.FAILED
            WorkInfo.State.CANCELLED -> GgufDownloadState.CANCELLED
        }
        val received = progress.getLong(GgufDownloadInput.KEY_RECEIVED_BYTES, 0L)
        val total = progress.getLong(GgufDownloadInput.KEY_TOTAL_BYTES, 0L)
        return GgufDownloadSnapshot(
            downloadKey = key,
            state = state,
            receivedBytes = received,
            totalBytes = total,
            errorMessage = outputData.getString(GgufDownloadInput.KEY_ERROR_MESSAGE)
        )
    }
}
