package dev.chungjungsoo.gptmobile.presentation.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.agent.AgentRunCoordinator
import dev.chungjungsoo.gptmobile.data.agent.AgentRunLimits
import dev.chungjungsoo.gptmobile.presentation.AppForegroundTracker
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AgentRunForegroundService : Service() {
    @Inject
    lateinit var coordinator: AgentRunCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var lastStartId = 0
    private var foregroundStarted = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val completionPreviews = mutableMapOf<Int, String>()
    private val cpuWakeLock = AgentRunCpuWakeLock(
        acquire = { timeoutMs -> acquireAgentRunWakeLock(timeoutMs) },
        release = { releaseAgentRunWakeLock() },
        isHeld = { wakeLock?.isHeld == true }
    )

    override fun onCreate() {
        super.onCreate()
        ensureGenerationNotificationChannels(this)
        var wasActive = coordinator.activeRuns.value.isNotEmpty()
        var previouslyActiveChats = coordinator.activeRuns.value.values.mapTo(mutableSetOf()) { it.chatId }
        if (!showNotification()) {
            coordinator.interruptAll()
            cpuWakeLock.releaseHeld()
            stopSelf()
            return
        }
        cpuWakeLock.onActiveChanged(wasActive)
        serviceScope.launch {
            coordinator.activeRuns.collectLatest {
                val currentRuns = coordinator.activeRuns.value
                val currentChats = currentRuns.values.mapTo(mutableSetOf()) { it.chatId }
                val finishedChats = previouslyActiveChats - currentChats
                (currentChats - previouslyActiveChats).forEach { chatId -> completionPreviews.remove(chatId) }
                previouslyActiveChats = currentChats
                dismissCompletionNotifications(currentChats)
                if (shouldNotifyAgentRunsCompleted(
                        wasActive = finishedChats.isNotEmpty(),
                        isActive = false,
                        isAppBackground = AppForegroundTracker.isBackgrounded
                    )
                ) {
                    completionChatIds(finishedChats).forEach(::showCompletionNotification)
                }
                if (currentRuns.isEmpty()) {
                    wasActive = stopIfIdle()
                    return@collectLatest
                }
                if (!resumeActiveWork()) {
                    return@collectLatest
                }
                wasActive = true
                while (true) {
                    delay(AGENT_RUN_WAKE_LOCK_RENEWAL_DELAY_MS)
                    cpuWakeLock.refresh()
                }
            }
        }
        serviceScope.launch {
            var lastCharacters = -1
            var lastCount = -1
            coordinator.liveSnapshots.collect { snapshots ->
                val progress = generationProgress(coordinator.activeRuns.value.values, snapshots)
                progress.previewByChat.forEach { (chatId, preview) ->
                    if (preview.isNotBlank()) completionPreviews[chatId] = preview
                }
                if (progress.activeCount == 0) {
                    lastCharacters = -1
                    lastCount = -1
                    return@collect
                }
                if (!shouldRefreshProgressNotification(lastCharacters, progress.receivedCharacters, lastCount, progress.activeCount)) {
                    return@collect
                }
                lastCharacters = progress.receivedCharacters
                lastCount = progress.activeCount
                updateProgressNotification(progress)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (isGenerationStopAction(intent?.action)) {
            // Job cancellation is the existing stop path, including remote response-id cancellation.
            coordinator.cancelAll()
        }
        if (coordinator.activeRuns.value.isNotEmpty()) {
            resumeActiveWork()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (coordinator.activeRuns.value.isNotEmpty()) {
            coordinator.interruptAll()
        }
        cpuWakeLock.releaseHeld()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        coordinator.interruptAll()
        cpuWakeLock.releaseHeld()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun resumeActiveWork(): Boolean {
        cpuWakeLock.onActiveChanged(true)
        dismissCompletionNotifications(coordinator.activeRuns.value.values.map { it.chatId })
        if (showNotification()) {
            return true
        }
        coordinator.interruptAll()
        cpuWakeLock.releaseHeld()
        requestStop()
        return false
    }

    private fun stopIfIdle(): Boolean {
        if (coordinator.activeRuns.value.isNotEmpty()) {
            resumeActiveWork()
            return true
        }
        cpuWakeLock.onActiveChanged(false)
        foregroundStarted = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        val stopped = requestStop()
        if (shouldResumeAfterIdleStop(stopped, coordinator.activeRuns.value.isNotEmpty())) {
            resumeActiveWork()
            return true
        }
        return false
    }

    private fun requestStop(): Boolean {
        val startId = lastStartId
        return if (startId == 0) {
            stopSelf()
            true
        } else {
            stopSelfResult(startId)
        }
    }

    private fun acquireAgentRunWakeLock(timeoutMs: Long) {
        val current = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .also { lock ->
                lock.setReferenceCounted(false)
                wakeLock = lock
            }
        current.acquire(timeoutMs)
    }

    private fun releaseAgentRunWakeLock() {
        wakeLock?.takeIf { lock -> lock.isHeld }?.let { lock ->
            runCatching { lock.release() }
        }
    }

    private fun currentProgress(): GenerationProgress = generationProgress(coordinator.activeRuns.value.values, coordinator.liveSnapshots.value)

    private fun showNotification(): Boolean = runCatching {
        val progress = currentProgress().let { progress ->
            if (progress.activeCount == 0) progress.copy(activeCount = 1) else progress
        }
        ServiceCompat.startForeground(
            this,
            ONGOING_NOTIFICATION_ID,
            buildNotification(progress),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        foregroundStarted = true
    }.isSuccess

    private fun updateProgressNotification(progress: GenerationProgress) {
        if (!foregroundStarted || progress.activeCount <= 0) return
        getSystemService(NotificationManager::class.java).notify(ONGOING_NOTIFICATION_ID, buildNotification(progress))
    }

    private fun buildNotification(progress: GenerationProgress): Notification {
        val activeCount = progress.activeCount.coerceAtLeast(1)
        val summary = resources.getQuantityString(R.plurals.agent_runs_active, activeCount, activeCount)
        val body = progress.preview.ifBlank { summary }
        val openApp = openChatPendingIntent(progress.chatIds.singleOrNull() ?: 0, requestCode = 0)
        val stopRuns = PendingIntent.getService(
            this,
            1,
            Intent(this, AgentRunForegroundService::class.java).setAction(ACTION_STOP_GENERATION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, GENERATION_PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gpt_mobile_monochrome_foreground)
            .setContentTitle(getString(R.string.agent_notification_title))
            .setContentText(body)
            .setSubText(getString(R.string.generation_progress_characters, progress.receivedCharacters))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(0, 0, true)
            .addAction(0, getString(R.string.stop_generation), stopRuns)
            .build()
    }

    private fun showCompletionNotification(chatId: Int) {
        if (chatId <= 0) return
        if (!shouldPostCompletionNotification(generationNotificationDelivery(GENERATION_COMPLETE_CHANNEL_ID))) return
        val preview = completionPreviews[chatId]?.takeIf { it.isNotBlank() }
        val text = preview ?: getString(R.string.agent_completion_notification_text)
        getSystemService(NotificationManager::class.java).notify(
            completionNotificationId(chatId),
            buildCompletionNotification(chatId, text)
        )
    }

    private fun buildCompletionNotification(chatId: Int, text: String): Notification {
        val replyIntent = Intent(this, GenerationReplyReceiver::class.java).apply {
            action = ACTION_REPLY_TO_CHAT
            putExtra(EXTRA_OPEN_CHAT_ID, chatId)
        }
        val replyPending = PendingIntent.getBroadcast(
            this,
            chatId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(EXTRA_REPLY_TEXT)
            .setLabel(getString(R.string.reply_hint))
            .build()
        val replyAction = NotificationCompat.Action.Builder(0, getString(R.string.reply_to_chat), replyPending)
            .addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        return NotificationCompat.Builder(this, GENERATION_COMPLETE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gpt_mobile_monochrome_foreground)
            .setContentTitle(getString(R.string.agent_completion_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openChatPendingIntent(chatId, requestCode = chatId + COMPLETION_NOTIFICATION_BASE))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(replyAction)
            .build()
    }

    private fun openChatPendingIntent(chatId: Int, requestCode: Int): PendingIntent {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            if (chatId > 0) putExtra(EXTRA_OPEN_CHAT_ID, chatId)
        }
        return PendingIntent.getActivity(
            this,
            requestCode,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dismissCompletionNotifications(chatIds: Collection<Int>) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        completionChatIds(chatIds.toSet()).forEach { chatId ->
            manager.cancel(completionNotificationId(chatId))
        }
    }

    companion object {
        private const val WAKE_LOCK_TAG = "dev.chungjungsoo.gptmobile:agent-run"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AgentRunForegroundService::class.java)
            )
        }
    }
}

internal fun shouldNotifyAgentRunsCompleted(
    wasActive: Boolean,
    isActive: Boolean,
    isAppBackground: Boolean
): Boolean = wasActive && !isActive && isAppBackground

internal fun shouldResumeAfterIdleStop(stopSelfSucceeded: Boolean, hasActiveRuns: Boolean): Boolean = !stopSelfSucceeded && hasActiveRuns

internal val AGENT_RUN_WAKE_LOCK_TIMEOUT_MS: Long = AgentRunLimits().runTimeoutMillis
internal val AGENT_RUN_WAKE_LOCK_RENEWAL_DELAY_MS: Long = AGENT_RUN_WAKE_LOCK_TIMEOUT_MS / 2

internal class AgentRunCpuWakeLock(
    private val acquire: (timeoutMs: Long) -> Unit,
    private val release: () -> Unit,
    private val isHeld: () -> Boolean,
    private val timeoutMs: Long = AGENT_RUN_WAKE_LOCK_TIMEOUT_MS
) {
    fun onActiveChanged(isActive: Boolean) {
        if (isActive) {
            refresh()
        } else {
            releaseHeld()
        }
    }

    fun refresh() {
        acquire(timeoutMs)
    }

    fun releaseHeld() {
        if (!isHeld()) return
        release()
    }
}
