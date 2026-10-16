package dev.chungjungsoo.gptmobile.data.crash

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Process-wide sink set from [android.app.Application.onCreate].
 * The log stays on disk. This object never opens a network connection.
 */
internal object CrashLogSink {
    @Volatile
    var log: LocalCrashLog? = null

    fun record(error: Throwable, threadName: String, fatal: Boolean) {
        log?.record(error, threadName, fatal)
    }
}

/**
 * Installed on the application scope (non-fatal) and discovered by the coroutines
 * ServiceLoader for every other uncaught coroutine failure (fatal).
 */
class AppCoroutineExceptionHandler(
    private val fatal: Boolean = true
) : CoroutineExceptionHandler {
    override val key: CoroutineContext.Key<*> = CoroutineExceptionHandler

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        if (exception is CancellationException) return
        CrashLogSink.record(exception, Thread.currentThread().name, fatal)
    }
}

internal fun crashUncaughtHandler(
    log: LocalCrashLog,
    previous: Thread.UncaughtExceptionHandler?
): Thread.UncaughtExceptionHandler = Thread.UncaughtExceptionHandler { thread, error ->
    runCatching { log.record(error, thread.name, fatal = true) }
    previous?.uncaughtException(thread, error)
}

internal fun installCrashHandlers(log: LocalCrashLog) {
    CrashLogSink.log = log
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    if (previous is RecordingUncaughtHandler) return
    Thread.setDefaultUncaughtExceptionHandler(RecordingUncaughtHandler(log, previous))
}

private class RecordingUncaughtHandler(
    log: LocalCrashLog,
    previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler by crashUncaughtHandler(log, previous)
