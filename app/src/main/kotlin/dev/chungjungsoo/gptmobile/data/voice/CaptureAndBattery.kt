package dev.chungjungsoo.gptmobile.data.voice

import android.content.Intent
import android.os.BatteryManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge

/**
 * Microphone capture is allowed only after the hands-free foreground notification is posted.
 * Revoking the gate is what stops a frame from being read as audio.
 */
@Singleton
class NotificationCaptureGate @Inject constructor() {
    private val visible = MutableStateFlow(false)
    val notificationVisible: StateFlow<Boolean> = visible.asStateFlow()

    fun grant() {
        visible.value = true
    }

    fun revoke() {
        visible.value = false
    }

    fun mayCapture(): Boolean = visible.value
}

object ForegroundCapture {
    fun begin(notificationPosted: Boolean, gate: NotificationCaptureGate): Boolean {
        if (!notificationPosted) {
            gate.revoke()
            return false
        }
        gate.grant()
        return gate.mayCapture()
    }

    fun end(gate: NotificationCaptureGate) {
        gate.revoke()
    }
}

class GatedVoiceRecognizer(
    private val gate: NotificationCaptureGate,
    private val delegate: VoiceRecognizer
) : VoiceRecognizer {
    private val blocked = MutableSharedFlow<VoiceTranscript>(extraBufferCapacity = 4)
    override val events = merge(delegate.events, blocked)
    override val levels = delegate.levels

    override suspend fun start() {
        if (!gate.mayCapture()) {
            blocked.emit(
                VoiceTranscript.Failure(
                    message = "Microphone stays off until the listening notification is visible.",
                    recoverable = true
                )
            )
            return
        }
        delegate.start()
    }

    override suspend fun stop() {
        delegate.stop()
    }
}

data class BatterySnapshot(
    val percent: Int,
    val charging: Boolean,
    val systemLow: Boolean
)

object BatteryStopPolicy {
    const val DEFAULT_THRESHOLD_PERCENT = 15

    fun shouldStop(snapshot: BatterySnapshot, thresholdPercent: Int = DEFAULT_THRESHOLD_PERCENT): Boolean {
        if (snapshot.charging) return false
        if (snapshot.systemLow) return true
        if (snapshot.percent < 0) return false
        return snapshot.percent <= thresholdPercent
    }

    fun snapshot(level: Int, scale: Int, status: Int, systemLow: Boolean): BatterySnapshot {
        val safeScale = scale.coerceAtLeast(1)
        val percent = if (level < 0) -1 else (level * 100) / safeScale
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatterySnapshot(percent = percent, charging = charging, systemLow = systemLow)
    }

    fun fromIntent(intent: Intent): BatterySnapshot = snapshot(
        level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100),
        status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
        systemLow = intent.action == Intent.ACTION_BATTERY_LOW
    )
}

data class VoiceLaunchRequest(
    val chatRoomId: Int,
    val enabledPlatforms: String,
    val nonce: Int
)

@Singleton
class VoiceSessionLaunch @Inject constructor() {
    private val requests = MutableStateFlow<VoiceLaunchRequest?>(null)
    val opens: StateFlow<VoiceLaunchRequest?> = requests.asStateFlow()
    private var nonce = 0

    fun open(chatRoomId: Int, enabledPlatforms: String) {
        nonce += 1
        requests.value = VoiceLaunchRequest(chatRoomId, enabledPlatforms, nonce)
    }

    fun consume() {
        requests.value = null
    }
}
