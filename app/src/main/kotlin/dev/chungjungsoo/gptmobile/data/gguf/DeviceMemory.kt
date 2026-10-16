package dev.chungjungsoo.gptmobile.data.gguf

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceMemory {
    fun totalBytes(): Long
    fun availableBytes(): Long
}

@Singleton
class AndroidDeviceMemory @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DeviceMemory {
    override fun totalBytes(): Long = memoryInfo().totalMem

    override fun availableBytes(): Long = memoryInfo().availMem

    private fun memoryInfo(): ActivityManager.MemoryInfo {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
    }
}
