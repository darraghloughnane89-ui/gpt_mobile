package dev.chungjungsoo.gptmobile.data.crash

import java.io.File
import java.time.Instant
import java.util.IdentityHashMap
import kotlinx.coroutines.CancellationException

/**
 * Append-only crash log stored in app-private files. Nothing here is uploaded.
 */
class LocalCrashLog(private val directory: File) {
    private val file = File(directory, FILE_NAME)
    private val recent = IdentityHashMap<Throwable, Long>()

    fun record(error: Throwable, threadName: String, fatal: Boolean): Boolean {
        if (error is CancellationException) return false
        return try {
            val now = System.currentTimeMillis()
            synchronized(recent) {
                val last = recent[error]
                if (last != null && now - last < DEDUPE_WINDOW_MS) return false
                recent[error] = now
                val iterator = recent.entries.iterator()
                while (iterator.hasNext()) {
                    if (now - iterator.next().value > DEDUPE_WINDOW_MS) iterator.remove()
                }
            }
            directory.mkdirs()
            file.appendText(formatEntry(error, threadName, fatal, Instant.ofEpochMilli(now)))
            trimToTail()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readText(): String = if (file.isFile) file.readText() else ""

    fun clear() {
        file.delete()
    }

    fun copyTo(destination: File) {
        destination.parentFile?.mkdirs()
        destination.writeText(readText())
    }

    private fun trimToTail() {
        if (file.length() <= MAX_BYTES) return
        val text = file.readText()
        val tail = text.takeLast(MAX_BYTES.toInt())
        val start = tail.indexOf(ENTRY_MARKER).let { index -> if (index >= 0) index else 0 }
        file.writeText(tail.substring(start))
    }

    companion object {
        const val DIRECTORY_NAME = "crash"
        const val FILE_NAME = "crash-log.txt"
        private const val MAX_BYTES = 256_000L
        private const val DEDUPE_WINDOW_MS = 2_000L
        private const val ENTRY_MARKER = "\n---\n"
    }
}

internal fun formatEntry(
    error: Throwable,
    threadName: String,
    fatal: Boolean,
    time: Instant
): String = buildString {
    append("\n---\n")
    append("fatal: ").append(fatal).append('\n')
    append("time: ").append(time).append('\n')
    append("thread: ").append(threadName).append('\n')
    append(error.stackTraceToString())
    if (!endsWith("\n")) append('\n')
}
