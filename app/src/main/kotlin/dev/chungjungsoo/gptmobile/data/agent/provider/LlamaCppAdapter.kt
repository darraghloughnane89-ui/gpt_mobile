package dev.chungjungsoo.gptmobile.data.agent.provider

import dev.chungjungsoo.gptmobile.data.agent.AgentProviderSession
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolExchange
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.gguf.DeviceMemory
import dev.chungjungsoo.gptmobile.data.gguf.GgufChatTurn
import dev.chungjungsoo.gptmobile.data.gguf.GgufModelStore
import dev.chungjungsoo.gptmobile.data.gguf.GgufRamPolicy
import dev.chungjungsoo.gptmobile.data.gguf.GgufRuntime
import dev.chungjungsoo.gptmobile.data.gguf.GgufSampling
import dev.chungjungsoo.gptmobile.data.gguf.RamFit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class LlamaCppAdapter(
    private val models: GgufModelStore,
    private val runtime: GgufRuntime,
    private val memory: DeviceMemory,
    private val missingModelMessage: String = DEFAULT_MISSING_MODEL,
    private val nativeUnavailableMessage: String = DEFAULT_NATIVE_UNAVAILABLE,
    private val attachmentsNotice: String = DEFAULT_ATTACHMENTS,
    private val waitingNotice: String = DEFAULT_WAITING,
    private val loadingNotice: String = DEFAULT_LOADING
) {
    fun openSession(turns: List<ConversationTurn>, platform: PlatformV2): AgentProviderSession = object : AgentProviderSession {
        override fun streamRound(
            tools: List<AgentToolDefinition>,
            exchanges: List<AgentToolExchange>
        ): Flow<ProviderEvent> = channelFlow {
            val record = models.get(platform.model)
            val file = record?.let(models::fileFor)
            if (record == null || file == null || !file.isFile) {
                send(ProviderEvent.Failed(missingModelMessage))
                return@channelFlow
            }
            val assessment = GgufRamPolicy.assess(record.sizeBytes, memory.totalBytes())
            if (assessment.fit == RamFit.REFUSE) {
                send(ProviderEvent.Failed(assessment.detail ?: missingModelMessage))
                return@channelFlow
            }
            if (assessment.fit == RamFit.WARN && !assessment.detail.isNullOrBlank()) {
                send(ProviderEvent.Notice(assessment.detail))
            }
            if (turns.any { turn -> turn.userMessage.attachments.isNotEmpty() || turn.assistantMessage?.attachments?.isNotEmpty() == true }) {
                send(ProviderEvent.Notice(attachmentsNotice))
            }
            if (runtime.isBusy && waitingNotice.isNotBlank()) {
                send(ProviderEvent.Notice(waitingNotice))
            } else if (loadingNotice.isNotBlank()) {
                send(ProviderEvent.Notice(loadingNotice))
            }
            val context = GgufRamPolicy.clampContext(record.contextSize, memory.totalBytes())
            val threads = threadCount(memory.totalBytes())
            val sampling = GgufSampling(
                contextSize = context,
                temperature = platform.temperature ?: record.temperature,
                topP = platform.topP ?: 0.95f,
                maxTokens = platform.maxTokens?.takeIf { it > 0 } ?: DEFAULT_MAX_TOKENS,
                threads = threads
            )
            try {
                runtime.stream(
                    modelPath = file.absolutePath,
                    systemPrompt = platform.systemPrompt,
                    messages = promptMessages(turns),
                    sampling = sampling
                ).collect { text ->
                    send(ProviderEvent.TextDelta(text))
                }
                send(ProviderEvent.Completed)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Throwable) {
                send(ProviderEvent.Failed(error.message?.takeIf { it.isNotBlank() } ?: nativeUnavailableMessage))
            }
        }
    }

    private fun promptMessages(turns: List<ConversationTurn>): List<GgufChatTurn> = buildList {
        turns.forEach { turn ->
            val user = turn.userMessage.effectiveContent().trim()
            if (user.isNotEmpty()) add(GgufChatTurn("user", user))
            val assistant = turn.assistantMessage?.effectiveContent()?.trim().orEmpty()
            if (assistant.isNotEmpty()) add(GgufChatTurn("assistant", assistant))
        }
    }

    private fun threadCount(totalMemBytes: Long): Int {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cap = if (totalMemBytes in 1 until LOW_RAM_THREAD_LIMIT) 2 else 4
        return cores.coerceAtMost(cap)
    }

    companion object {
        const val DEFAULT_MAX_TOKENS = 512
        const val DEFAULT_MISSING_MODEL = "This GGUF model is not on the device."
        const val DEFAULT_NATIVE_UNAVAILABLE = "On-device GGUF needs an arm64 device with the llama.cpp library."
        const val DEFAULT_ATTACHMENTS = "GGUF chat uses the message text. Image files stay on the device."
        const val DEFAULT_WAITING = "Waiting for the on-device model"
        const val DEFAULT_LOADING = "Loading GGUF model…"
        private const val LOW_RAM_THREAD_LIMIT = 4_000_000_000L
    }
}
