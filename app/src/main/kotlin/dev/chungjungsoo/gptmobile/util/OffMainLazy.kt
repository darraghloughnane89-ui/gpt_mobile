package dev.chungjungsoo.gptmobile.util

import android.os.Looper
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Creates a heavy singleton the first time it is needed, and always runs that work
 * off the main thread. Later reads return the same instance.
 */
class OffMainLazy<T>(
    private val name: String,
    private val isMainThread: () -> Boolean = MainThreadProbe::isMainThread,
    private val executor: ExecutorService = OffMainExecutors.executor,
    private val initializer: () -> T
) {
    @Volatile
    private var value: Any? = UNINITIALIZED

    private val lock = Any()

    val isInitialized: Boolean
        get() = value !== UNINITIALIZED

    fun get(): T {
        val current = value
        if (current !== UNINITIALIZED) {
            @Suppress("UNCHECKED_CAST")
            return current as T
        }
        if (isMainThread()) {
            try {
                return executor.submit<T> { initialize() }.get()
            } catch (error: ExecutionException) {
                throw error.cause ?: error
            }
        }
        return initialize()
    }

    private fun initialize(): T {
        synchronized(lock) {
            val current = value
            if (current !== UNINITIALIZED) {
                @Suppress("UNCHECKED_CAST")
                return current as T
            }
            check(!isMainThread()) {
                "$name must be initialized off the main thread"
            }
            val created = initializer()
            value = created
            return created
        }
    }

    private companion object {
        val UNINITIALIZED = Any()
    }
}

object MainThreadProbe {
    fun isMainThread(): Boolean = try {
        val mainLooper = Looper.getMainLooper() ?: return false
        mainLooper.thread == Thread.currentThread()
    } catch (_: RuntimeException) {
        false
    }
}

internal object OffMainExecutors {
    private val threadCount = AtomicInteger()

    val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "off-main-singleton-${threadCount.incrementAndGet()}").apply {
            isDaemon = true
        }
    }
}
