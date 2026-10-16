package dev.chungjungsoo.gptmobile.presentation

import android.os.StrictMode

internal fun installStrictModeIfDebug(isDebug: Boolean, install: () -> Unit) {
    if (isDebug) install()
}

internal fun enableDebugStrictMode() {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectAll()
            .penaltyLog()
            .build()
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectAll()
            .penaltyLog()
            .build()
    )
}
