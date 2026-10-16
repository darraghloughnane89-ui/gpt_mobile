package dev.chungjungsoo.gptmobile.data.crash

import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCrashLogTest {
    @Test
    fun recordReadsAndCopiesWithoutUploading() {
        val directory = tempDir()
        val log = LocalCrashLog(directory)
        val error = IllegalStateException("boom")

        assertTrue(log.record(error, "main", fatal = true))

        val text = log.readText()
        assertTrue(text.contains("fatal: true"))
        assertTrue(text.contains("thread: main"))
        assertTrue(text.contains("IllegalStateException"))
        assertTrue(text.contains("boom"))
        val copy = File(directory, "export.txt")
        log.copyTo(copy)
        assertEquals(text, copy.readText())
    }

    @Test
    fun duplicateThrowableInsideTheWindowIsStoredOnce() {
        val log = LocalCrashLog(tempDir())
        val error = IllegalStateException("same")

        assertTrue(log.record(error, "main", fatal = false))
        assertFalse(log.record(error, "main", fatal = false))
        assertEquals(1, log.readText().split("\n---\n").count { it.contains("same") })
    }

    @Test
    fun cancellationIsNotACrash() {
        val log = LocalCrashLog(tempDir())

        assertFalse(log.record(CancellationException("stopped"), "main", fatal = false))
        assertEquals("", log.readText())
    }

    @Test
    fun diskFailureDoesNotEscapeTheRecorder() {
        val blocker = File(tempDir(), "not-a-directory")
        blocker.writeText("x")
        val log = LocalCrashLog(File(blocker, "crash"))

        assertFalse(log.record(IllegalStateException("disk"), "main", fatal = true))
    }

    @Test
    fun clearRemovesTheLocalFile() {
        val log = LocalCrashLog(tempDir())
        log.record(IllegalStateException("gone"), "main", fatal = true)

        log.clear()

        assertEquals("", log.readText())
    }

    @Test
    fun uncaughtHandlerRecordsThenDelegates() {
        val log = LocalCrashLog(tempDir())
        val error = IllegalStateException("fatal")
        var delegated: Throwable? = null
        val handler = crashUncaughtHandler(log) { _, thrown -> delegated = thrown }

        handler.uncaughtException(Thread.currentThread(), error)

        assertSame(error, delegated)
        assertTrue(log.readText().contains("fatal: true"))
        assertTrue(log.readText().contains("fatal"))
    }

    @Test
    fun coroutineHandlerLogsBackgroundFailuresAndSkipsCancellation() {
        val log = LocalCrashLog(tempDir())
        CrashLogSink.log = log
        try {
            AppCoroutineExceptionHandler(fatal = false)
                .handleException(EmptyCoroutineContext, IllegalStateException("background"))
            val stored = log.readText()
            assertTrue(stored.contains("fatal: false"))
            assertTrue(stored.contains("background"))

            AppCoroutineExceptionHandler()
                .handleException(EmptyCoroutineContext, CancellationException("stop"))

            assertEquals(stored, log.readText())
        } finally {
            CrashLogSink.log = null
        }
    }

    @Test
    fun installDoesNotWrapTheHandlerTwice() {
        val log = LocalCrashLog(tempDir())
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        try {
            installCrashHandlers(log)
            val installed = Thread.getDefaultUncaughtExceptionHandler()
            installCrashHandlers(log)
            assertSame(installed, Thread.getDefaultUncaughtExceptionHandler())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
            CrashLogSink.log = null
        }
    }

    @Test
    fun serviceLoaderPointsAtTheLocalHandler() {
        val file = listOf(
            File("src/main/resources/META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler"),
            File("app/src/main/resources/META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler")
        ).first { it.isFile }
        assertEquals(AppCoroutineExceptionHandler::class.java.name, file.readText().trim())
    }

    private fun tempDir(): File = File.createTempFile("crash-log", "").apply {
        delete()
        mkdirs()
        deleteOnExit()
    }
}
