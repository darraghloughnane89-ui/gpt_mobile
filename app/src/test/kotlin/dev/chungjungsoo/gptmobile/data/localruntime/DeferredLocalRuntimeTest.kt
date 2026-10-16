package dev.chungjungsoo.gptmobile.data.localruntime

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeferredLocalRuntimeTest {
    @Test
    fun factoryStaysIdleUntilTheEngineIsLoaded() = runTest {
        var created = 0
        val runtime = DeferredLocalRuntime(isMainThread = { false }) {
            created += 1
            FakeLocalRuntime()
        }

        runtime.cancelActive()
        assertFalse(runtime.hasOpenConversation())
        assertEquals(0, created)

        runtime.loadEngine(LocalEngineSpec(modelPath = "/models/bench.gguf", accelerator = "cpu", maxTokens = 32))
        runtime.loadEngine(LocalEngineSpec(modelPath = "/models/bench.gguf", accelerator = "cpu", maxTokens = 32))

        assertEquals(1, created)
    }
}
