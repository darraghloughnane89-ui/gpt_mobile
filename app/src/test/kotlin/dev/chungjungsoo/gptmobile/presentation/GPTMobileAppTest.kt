package dev.chungjungsoo.gptmobile.presentation

import android.content.ComponentCallbacks2
import dev.chungjungsoo.gptmobile.data.localruntime.FakeLocalRuntime
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GPTMobileAppTest {
    @Test
    fun `startup recovery gate waits for current maintenance job`() = runTest {
        val events = mutableListOf<String>()

        StartupRecoveryGate.start(this) { events += "recover" }
        StartupRecoveryGate.await()
        events += "observe"

        assertEquals(listOf("recover", "observe"), events)
    }

    @Test
    fun `startup interrupts persisted work before migrating credentials`() = runTest {
        val events = mutableListOf<String>()

        runStartupMaintenance(
            interruptPersistedWork = { events += "interrupt" },
            migrateSecrets = {
                events += "migrate"
                emptyList()
            }
        )

        assertEquals(listOf("interrupt", "migrate"), events)
    }

    @Test
    fun `trim memory routes idle trim instead of unload`() = runTest {
        val runtime = FakeLocalRuntime()

        trimLocalRuntimeForMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, runtime)

        assertEquals(1, runtime.trimIdleEngineCalls)
        assertEquals(0, runtime.unloadEngineCalls)
        assertEquals(0, runtime.cancelActiveCalls)
    }

    @Test
    fun `trim memory ignores levels below running low`() = runTest {
        val runtime = FakeLocalRuntime()

        trimLocalRuntimeForMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE, runtime)

        assertEquals(0, runtime.trimIdleEngineCalls)
        assertEquals(0, runtime.unloadEngineCalls)
    }

    @Test
    fun `staged backup restore runs on the injected dispatcher`() = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "backup-io") }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            var threadName: String? = null
            restoreStagedBackup(dispatcher) { threadName = Thread.currentThread().name }
            assertEquals("backup-io", threadName)
        } finally {
            dispatcher.close()
            executor.shutdown()
        }
    }
}
