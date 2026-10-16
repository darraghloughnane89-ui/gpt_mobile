package dev.chungjungsoo.gptmobile.data.localruntime

import dev.chungjungsoo.gptmobile.util.MainThreadProbe
import dev.chungjungsoo.gptmobile.util.OffMainLazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

/**
 * Loads the on-device engine the first time a conversation needs it.
 * LiteRT-LM's native layer is the llama.cpp-class runtime; constructing it loads that
 * library, so the factory always runs off the main thread.
 */
class DeferredLocalRuntime(
    isMainThread: () -> Boolean = MainThreadProbe::isMainThread,
    private val factory: () -> LocalRuntime
) : LocalRuntime {
    private val gate = OffMainLazy(name = "litert-lm", isMainThread = isMainThread) {
        factory()
    }

    private suspend fun delegate(): LocalRuntime = withContext(Dispatchers.IO) {
        gate.get()
    }

    override suspend fun loadEngine(spec: LocalEngineSpec) {
        delegate().loadEngine(spec)
    }

    override suspend fun createConversation(config: LocalConversationConfig) {
        delegate().createConversation(config)
    }

    override fun sendMessage(text: String, images: List<ByteArray>): Flow<LocalRuntimeEvent> = channelFlow {
        delegate().sendMessage(text, images).collect { send(it) }
    }

    override fun cancelActive() {
        if (!gate.isInitialized) return
        gate.get().cancelActive()
    }

    override suspend fun closeConversation() {
        if (!gate.isInitialized) return
        delegate().closeConversation()
    }

    override suspend fun unloadEngine() {
        if (!gate.isInitialized) return
        delegate().unloadEngine()
    }

    override suspend fun trimIdleEngine() {
        if (!gate.isInitialized) return
        delegate().trimIdleEngine()
    }

    override fun isEngineLoaded(spec: LocalEngineSpec): Boolean = gate.isInitialized && gate.get().isEngineLoaded(spec)

    override fun hasOpenConversation(): Boolean = gate.isInitialized && gate.get().hasOpenConversation()

    override suspend fun <T> runExclusive(block: suspend LocalRuntime.() -> T): T = delegate().runExclusive(block)

    override fun <T> runExclusiveFlow(
        onContended: suspend () -> Unit,
        block: suspend LocalRuntime.() -> Flow<T>
    ): Flow<T> = channelFlow {
        delegate().runExclusiveFlow(onContended, block).collect { send(it) }
    }
}
