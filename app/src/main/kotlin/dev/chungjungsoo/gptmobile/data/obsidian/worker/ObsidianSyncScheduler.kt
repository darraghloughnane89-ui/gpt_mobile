package dev.chungjungsoo.gptmobile.data.obsidian.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.obsidian.VaultBackend
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObsidianSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun ensurePeriodic(backend: VaultBackend) {
        val constraints = Constraints.Builder().apply {
            if (backend == VaultBackend.GITHUB) {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }
        }.build()
        val request = PeriodicWorkRequestBuilder<ObsidianSyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    companion object {
        const val PERIODIC_WORK = "robin_obsidian_sync"
        const val PERIOD_HOURS = 6L
    }
}
