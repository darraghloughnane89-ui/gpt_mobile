package dev.chungjungsoo.gptmobile.data.obsidian.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.chungjungsoo.gptmobile.data.obsidian.ObsidianSyncEngine

@HiltWorker
class ObsidianSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: ObsidianSyncEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        engine.sync()
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
