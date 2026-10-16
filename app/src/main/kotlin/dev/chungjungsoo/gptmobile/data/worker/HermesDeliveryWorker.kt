package dev.chungjungsoo.gptmobile.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.hermes.HermesDeliveryProcessor
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity

@HiltWorker
class HermesDeliveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: HermesDeliveryProcessor
) : CoroutineWorker(context, params) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    override suspend fun doWork(): Result {
        ensureChannel()
        runCatching { setForeground(createForegroundInfo()) }
        val retry = processor.drain(runAttemptCount)
        return if (retry) Result.retry() else Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel()
        return createForegroundInfo()
    }

    private fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.hermes_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = applicationContext.getString(R.string.hermes_notification_channel_description)
            }
        )
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.hermes_notification_title))
            .setContentText(applicationContext.getString(R.string.hermes_notification_body))
            .setSmallIcon(R.drawable.ic_gpt_mobile_monochrome_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private companion object {
        const val CHANNEL_ID = "hermes_delivery"
        const val NOTIFICATION_ID = 8642
    }
}
