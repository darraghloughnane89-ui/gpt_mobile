package dev.chungjungsoo.gptmobile.data.gguf

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.ladenthin.llama.LlamaModel
import net.ladenthin.llama.parameters.InferenceParameters
import net.ladenthin.llama.parameters.ModelParameters
import net.ladenthin.llama.value.Pair as LlamaPair

/**
 * On-device GGUF chat through llama.cpp.
 *
 * The native library is `net.ladenthin:llama-android`, an MIT Java binding of llama.cpp.
 * Packaging keeps `arm64-v8a` and drops the x86_64, x86, and armeabi-v7a copies.
 * SmolChat-Android's `SmolLM` (Apache-2.0) is the reference for this session shape:
 * one loaded model, a streaming completion loop, and stop on cancellation.
 * Its `smollm` JNI module is not vendored: that build also produces armeabi-v7a and
 * several extra arm64 variants, and it compiles the llama.cpp submodule from source.
 */
@Singleton
class LlamaCppGgufEngine @Inject constructor() : GgufRuntime {
    private val mutex = Mutex()
    private var loadedPath: String? = null
    private var loadedContext: Int = 0
    private var model: LlamaModel? = null

    override val isBusy: Boolean
        get() = mutex.isLocked

    override fun stream(
        modelPath: String,
        systemPrompt: String?,
        messages: List<GgufChatTurn>,
        sampling: GgufSampling
    ): Flow<String> = flow {
        val job = currentCoroutineContext()[Job]
        mutex.withLock {
            val llama = load(modelPath, sampling)
            val parameters = InferenceParameters("")
                .withMessages(systemPrompt.orEmpty(), chatPairs(messages))
                .withUseChatTemplate(true)
                .withTemperature(sampling.temperature)
                .withTopP(sampling.topP)
                .withNPredict(sampling.maxTokens)
            llama.generateChat(parameters).use { iterable ->
                for (output in iterable) {
                    job?.ensureActive()
                    if (output.text.isNotEmpty()) emit(output.text)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun chatPairs(messages: List<GgufChatTurn>): List<LlamaPair<String, String>> = messages.map { turn ->
        LlamaPair(turn.role, turn.content)
    }

    private fun load(modelPath: String, sampling: GgufSampling): LlamaModel {
        val current = model
        if (current != null && loadedPath == modelPath && loadedContext == sampling.contextSize) {
            return current
        }
        current?.close()
        model = null
        val parameters = ModelParameters()
            .setModel(modelPath)
            .setCtxSize(sampling.contextSize)
            .setThreads(sampling.threads)
            .setThreadsBatch(sampling.threads)
            .setBatchSize(if (sampling.threads <= 2) 128 else 512)
            .setUbatchSize(if (sampling.threads <= 2) 64 else 256)
            .setGpuLayers(0)
            .setParallel(1)
            .setFit(true)
        val created = LlamaModel(parameters)
        model = created
        loadedPath = modelPath
        loadedContext = sampling.contextSize
        return created
    }
}
