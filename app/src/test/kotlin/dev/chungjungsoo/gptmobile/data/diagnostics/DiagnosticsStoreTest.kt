package dev.chungjungsoo.gptmobile.data.diagnostics

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun writeCrash_storesRedactedFileWithoutChatOrSecrets() {
        val store = DiagnosticsStore(folder.root)
        val secret = "sk-proj-abcdefghijklmnopqrstuvwxyz"
        val crash = IllegalStateException(
            "token=$secret User: diary about the harbor {\"content\":\"private transcript\"}"
        )

        val file = store.writeCrash("main", crash, timeMs = 1_700_000_000_000)
        val text = file.readText()

        assertTrue(file.parentFile?.name == DiagnosticsStore.CRASH_DIR)
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("thread: main"))
        assertFalse(text.contains(secret))
        assertFalse(text.contains("diary about the harbor"))
        assertFalse(text.contains("private transcript"))
        assertEquals(1, store.listCrashes().size)
    }

    @Test
    fun providerErrorAndShareReport_omitSecretsAndChat() {
        val store = DiagnosticsStore(folder.root)
        val secret = "gsk_abcdefghijklmnopqrstuvwxyz123456"
        recordProviderFailure(
            store = store,
            platformName = "OpenAI",
            model = "gpt-4o",
            message = "HTTP 429 rate limit. api_key=$secret User: secret prompt {\"content\":\"chat body\"}",
            timeMs = 50
        )

        val stored = store.listProviderErrors().single()
        val report = store.buildShareReport()

        assertEquals(ProviderFailureKind.RATE_LIMIT, stored.kind)
        assertEquals("OpenAI", stored.platform)
        assertFalse(stored.detail.contains(secret))
        assertFalse(stored.detail.contains("secret prompt"))
        assertFalse(stored.detail.contains("chat body"))
        assertFalse(report.contains(secret))
        assertFalse(report.contains("secret prompt"))
        assertFalse(report.contains("chat body"))
        assertTrue(report.contains("RATE_LIMIT"))
    }

    @Test
    fun uncaughtHandler_writesCrashAndForwards() {
        val store = DiagnosticsStore(File(folder.root, "diagnostics"))
        var forwarded: Throwable? = null
        val handler = LocalUncaughtExceptionHandler(
            store,
            Thread.UncaughtExceptionHandler { _, throwable -> forwarded = throwable }
        )
        val failure = IllegalStateException("password=hunter2hunter2")

        handler.uncaughtException(Thread.currentThread(), failure)

        assertEquals(failure, forwarded)
        val crash = store.listCrashes().single()
        val text = File(folder.root, "diagnostics/${DiagnosticsStore.CRASH_DIR}/${crash.fileName}").readText()
        assertFalse(text.contains("hunter2hunter2"))
        assertTrue(text.contains("IllegalStateException"))
    }
}
