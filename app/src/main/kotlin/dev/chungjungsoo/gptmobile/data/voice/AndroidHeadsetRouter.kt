package dev.chungjungsoo.gptmobile.data.voice

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidHeadsetRouter(
    context: Context
) : HeadsetRouter {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val headset = MutableStateFlow(false)
    override val usingHeadset = headset.asStateFlow()
    private var engaged = false
    private var previousMode = AudioManager.MODE_NORMAL
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (engaged) routeToHeadset()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (engaged) routeToHeadset()
        }
    }

    override fun engage() {
        if (engaged) return
        previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        engaged = true
        routeToHeadset()
    }

    override fun restore() {
        if (!engaged) return
        engaged = false
        runCatching { audioManager.unregisterAudioDeviceCallback(callback) }
        runCatching { audioManager.clearCommunicationDevice() }
        audioManager.mode = previousMode
        headset.value = false
    }

    private fun routeToHeadset() {
        val selected = runCatching { selectDevice() }.getOrElse { error ->
            Log.w(TAG, "Bluetooth headset routing is unavailable", error)
            null
        }
        if (selected == null) {
            runCatching { audioManager.clearCommunicationDevice() }
            headset.value = false
            return
        }
        val routed = runCatching { audioManager.setCommunicationDevice(selected) }.getOrDefault(false)
        headset.value = routed
    }

    private fun selectDevice(): AudioDeviceInfo? {
        val devices = audioManager.availableCommunicationDevices
        val preferred = preferredCommunicationDevice(devices.map { it.type }) ?: return null
        return devices.firstOrNull { it.type == preferred }
    }

    private companion object {
        const val TAG = "VoiceHeadset"
    }
}
