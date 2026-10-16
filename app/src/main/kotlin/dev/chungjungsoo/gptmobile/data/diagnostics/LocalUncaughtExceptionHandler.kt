package dev.chungjungsoo.gptmobile.data.diagnostics

import android.content.Context
import java.io.File

/**
 * Writes a redacted crash file on this device, then forwards to the previous handler.
 * Nothing is uploaded.
 */
class LocalUncaughtExceptionHandler(
    private val store: DiagnosticsStore,
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            store.writeCrash(thread.name ?: "unknown", throwable)
        } catch (_: Throwable) {
            // Keep the platform handler reachable if local storage fails.
        }
        previous?.uncaughtException(thread, throwable)
    }
}

fun installLocalUncaughtExceptionHandler(context: Context) {
    val current = Thread.getDefaultUncaughtExceptionHandler()
    if (current is LocalUncaughtExceptionHandler) return
    val store = DiagnosticsStore(File(context.filesDir, "diagnostics"))
    Thread.setDefaultUncaughtExceptionHandler(LocalUncaughtExceptionHandler(store, current))
}
