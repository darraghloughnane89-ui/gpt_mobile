package dev.chungjungsoo.gptmobile.presentation.phone.widget

import android.app.KeyguardManager
import android.content.Context

internal fun Context.isDeviceLocked(): Boolean {
    val keyguard = getSystemService(KeyguardManager::class.java) ?: return false
    return keyguard.isDeviceLocked || keyguard.isKeyguardLocked
}
