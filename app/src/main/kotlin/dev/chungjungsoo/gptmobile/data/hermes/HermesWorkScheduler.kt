package dev.chungjungsoo.gptmobile.data.hermes

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.data.worker.HermesDeliveryWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerHermesScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : HermesWorkScheduler {
    override fun enqueueDrain() {
        val request = OneTimeWorkRequestBuilder<HermesDeliveryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            HermesDefaults.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }
}

class MemoryHermesWorkScheduler : HermesWorkScheduler {
    var calls: Int = 0
        private set

    override fun enqueueDrain() {
        calls += 1
    }
}
