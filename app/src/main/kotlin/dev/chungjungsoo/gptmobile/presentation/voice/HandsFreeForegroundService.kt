package dev.chungjungsoo.gptmobile.presentation.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.voice.BatteryStopPolicy
import dev.chungjungsoo.gptmobile.data.voice.ForegroundCapture
import dev.chungjungsoo.gptmobile.data.voice.HandsFreePreferences
import dev.chungjungsoo.gptmobile.data.voice.NotificationCaptureGate
import dev.chungjungsoo.gptmobile.data.voice.OpenWakeWordEngine
import dev.chungjungsoo.gptmobile.data.voice.WakeFrameKind
import dev.chungjungsoo.gptmobile.data.voice.WakeFrameProcessor
import dev.chungjungsoo.gptmobile.data.voice.WakePhraseEngine
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class HandsFreeForegroundService : Service() {
    @Inject
    lateinit var gate: NotificationCaptureGate

    @Inject
    lateinit var engine: WakePhraseEngine

    @Inject
    lateinit var preferences: HandsFreePreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeJob: Job? = null

    @Volatile
    private var conversationMode = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private val processor by lazy { WakeFrameProcessor(gate, engine) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown(clearWakePreference = true)
                return START_NOT_STICKY
            }
            ACTION_CONVERSATION -> conversationMode = true
            ACTION_WAKE -> conversationMode = false
        }
        if (!hasMicrophonePermission()) {
            ForegroundCapture.end(gate)
            stopSelf()
            return START_NOT_STICKY
        }
        val posted = postNotification(conversationMode)
        if (!ForegroundCapture.begin(posted, gate)) {
            stopSelf()
            return START_NOT_STICKY
        }
        watchBattery()
        if (conversationMode) {
            stopWakeLoop()
        } else {
            startWakeLoop()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        shutdown(clearWakePreference = false)
    }

    override fun onDestroy() {
        ForegroundCapture.end(gate)
        stopWakeLoop()
        releaseWakeLock()
        unregisterBattery()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startWakeLoop() {
        if (wakeJob?.isActive == true) return
        engine.reset()
        acquireWakeLock()
        wakeJob = serviceScope.launch(Dispatchers.IO) {
            captureWakeFrames()
        }
    }

    private fun stopWakeLoop() {
        wakeJob?.cancel()
        wakeJob = null
        releaseWakeLock()
    }

    private fun captureWakeFrames() {
        if (!gate.mayCapture()) return
        val minBuffer = AudioRecord.getMinBufferSize(
            OpenWakeWordEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            OpenWakeWordEngine.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, OpenWakeWordEngine.FRAME_SAMPLES * 2)
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED || !gate.mayCapture()) {
            recorder.release()
            return
        }
        recorder.startRecording()
        val frame = ShortArray(OpenWakeWordEngine.FRAME_SAMPLES)
        try {
            while (gate.mayCapture() && !conversationMode) {
                val read = recorder.read(frame, 0, frame.size)
                if (read <= 0) continue
                val chunk = if (read == frame.size) frame.clone() else frame.copyOf(read)
                val result = processor.onPcm(chunk, System.currentTimeMillis())
                if (result.kind == WakeFrameKind.BLOCKED) break
                if (result.kind == WakeFrameKind.DETECTED) {
                    conversationMode = true
                    onWakePhrase()
                    break
                }
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private fun onWakePhrase() {
        serviceScope.launch {
            val session = preferences.current()
            val open = Intent(this@HandsFreeForegroundService, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_VOICE, true)
                putExtra(EXTRA_CHAT_ROOM_ID, session.chatRoomId)
                putExtra(EXTRA_PLATFORMS, session.enabledPlatforms)
            }
            startActivity(open)
            postNotification(conversation = true)
        }
    }

    private fun watchBattery() {
        if (batteryReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                serviceScope.launch {
                    val threshold = preferences.current().batteryStopPercent
                    if (BatteryStopPolicy.shouldStop(BatteryStopPolicy.fromIntent(intent), threshold)) {
                        shutdown(clearWakePreference = false)
                    }
                }
            }
        }
        batteryReceiver = receiver
        val sticky = registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) receiver.onReceive(this, sticky)
    }

    private fun shutdown(clearWakePreference: Boolean) {
        conversationMode = true
        ForegroundCapture.end(gate)
        stopWakeLoop()
        unregisterBattery()
        if (clearWakePreference) {
            runBlocking(Dispatchers.IO) { preferences.setWakeEnabled(false) }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun postNotification(conversation: Boolean): Boolean = runCatching {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(conversation),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }.isSuccess

    private fun buildNotification(conversation: Boolean): Notification {
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, HandsFreeForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_VOICE, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (conversation) {
            getString(R.string.hands_free_notification_conversation)
        } else {
            getString(R.string.hands_free_notification_listening)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_gpt_mobile_monochrome_foreground)
            .setContentTitle(getString(R.string.hands_free_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(0, getString(R.string.hands_free_stop), stop)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.hands_free_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = getString(R.string.hands_free_channel_description)
        manager.createNotificationChannel(channel)
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun acquireWakeLock() {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            .also { created ->
                created.setReferenceCounted(false)
                wakeLock = created
            }
        lock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.let { lock ->
            runCatching { lock.release() }
        }
    }

    private fun unregisterBattery() {
        batteryReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        batteryReceiver = null
    }

    companion object {
        const val ACTION_WAKE = "dev.chungjungsoo.gptmobile.handsfree.WAKE"
        const val ACTION_CONVERSATION = "dev.chungjungsoo.gptmobile.handsfree.CONVERSATION"
        const val ACTION_STOP = "dev.chungjungsoo.gptmobile.handsfree.STOP"
        const val EXTRA_OPEN_VOICE = "dev.chungjungsoo.gptmobile.OPEN_VOICE_SESSION"
        const val EXTRA_CHAT_ROOM_ID = "dev.chungjungsoo.gptmobile.VOICE_CHAT_ROOM"
        const val EXTRA_PLATFORMS = "dev.chungjungsoo.gptmobile.VOICE_PLATFORMS"
        private const val CHANNEL_ID = "hands_free_listening"
        private const val NOTIFICATION_ID = 47
        private const val WAKE_LOCK_TAG = "gptmobile:hands-free"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        fun startWake(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, HandsFreeForegroundService::class.java).setAction(ACTION_WAKE)
            )
        }

        fun startConversation(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, HandsFreeForegroundService::class.java).setAction(ACTION_CONVERSATION)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, HandsFreeForegroundService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
