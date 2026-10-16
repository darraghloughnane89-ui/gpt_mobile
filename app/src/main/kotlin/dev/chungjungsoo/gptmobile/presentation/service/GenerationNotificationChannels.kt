package dev.chungjungsoo.gptmobile.presentation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.chungjungsoo.gptmobile.R

fun ensureGenerationNotificationChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val progress = NotificationChannel(
        GENERATION_PROGRESS_CHANNEL_ID,
        context.getString(R.string.agent_notification_channel),
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = context.getString(R.string.agent_notification_channel_description)
        setShowBadge(false)
    }
    val finished = NotificationChannel(
        GENERATION_COMPLETE_CHANNEL_ID,
        context.getString(R.string.generation_complete_channel),
        NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
        description = context.getString(R.string.generation_complete_channel_description)
    }
    manager.createNotificationChannel(progress)
    manager.createNotificationChannel(finished)
}

fun generationChannelSettingsIntent(packageName: String, channelId: String): Intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
}

internal fun Context.generationNotificationDelivery(channelId: String): NotificationDelivery {
    val permissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val manager = getSystemService(NotificationManager::class.java)
    val channel = manager?.getNotificationChannel(channelId)
    val state = when {
        channel == null -> ChannelBlockState.ABSENT
        channel.importance == NotificationManager.IMPORTANCE_NONE -> ChannelBlockState.BLOCKED
        else -> ChannelBlockState.OPEN
    }
    return notificationDelivery(
        sdkInt = Build.VERSION.SDK_INT,
        permissionGranted = permissionGranted,
        appNotificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled(),
        channel = state
    )
}
