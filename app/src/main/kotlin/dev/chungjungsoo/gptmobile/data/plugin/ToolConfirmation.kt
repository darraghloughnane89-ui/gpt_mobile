package dev.chungjungsoo.gptmobile.data.plugin

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject

enum class ToolEffect {
    READ,
    WRITE
}

data class ToolConfirmRequest(
    val toolName: String,
    val pluginName: String,
    val detail: String
)

fun interface ToolConfirmPrompter {
    suspend fun confirm(request: ToolConfirmRequest): Boolean
}

object AllowToolConfirmPrompter : ToolConfirmPrompter {
    override suspend fun confirm(request: ToolConfirmRequest): Boolean = true
}

/**
 * Present while a routine runs. Write tools are refused instead of waiting for a dialog.
 */
class UnattendedToolCall : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<UnattendedToolCall>
}

class PluginToolFilter(
    val pluginUids: Set<String>
) : kotlin.coroutines.AbstractCoroutineContextElement(Key) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<PluginToolFilter>
}

fun classifyToolEffect(
    name: String,
    description: String,
    readOnlyHint: Boolean? = null,
    destructiveHint: Boolean? = null
): ToolEffect {
    if (readOnlyHint == true) return ToolEffect.READ
    if (destructiveHint == true) return ToolEffect.WRITE
    val tokens = "$name $description".lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }.toSet()
    val write = WRITE_WORDS.any { it in tokens }
    val read = READ_WORDS.any { it in tokens }
    return if (write) {
        ToolEffect.WRITE
    } else if (read) {
        ToolEffect.READ
    } else {
        ToolEffect.WRITE
    }
}

class GatedAgentTool(
    override val definition: AgentToolDefinition,
    private val effect: ToolEffect,
    private val enabled: Boolean,
    private val pluginName: String,
    private val prompter: ToolConfirmPrompter,
    private val delegate: AgentTool
) : AgentTool {
    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
        if (!enabled) {
            return denied(callId, "Tool '${definition.name}' is disabled.")
        }
        if (effect == ToolEffect.WRITE) {
            val unattended = coroutineContext[UnattendedToolCall] != null
            val allowed = !unattended &&
                prompter.confirm(
                    ToolConfirmRequest(
                        toolName = definition.name,
                        pluginName = pluginName,
                        detail = "This can change data outside the chat. Allow ${definition.name}?"
                    )
                )
            if (!allowed) {
                return denied(callId, "Write action '${definition.name}' was not confirmed.")
            }
        }
        return delegate.execute(callId, arguments)
    }

    private fun denied(callId: String, message: String) = AgentToolResult(
        callId = callId,
        content = ToolResultContent.Text(message),
        isError = true
    )
}

@Singleton
class ToolConfirmBroker @Inject constructor() : ToolConfirmPrompter {
    data class Pending(
        val id: Long,
        val toolName: String,
        val pluginName: String,
        val detail: String
    )

    private val ids = AtomicLong()
    private val waiters = java.util.concurrent.ConcurrentHashMap<Long, CompletableDeferred<Boolean>>()
    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    @Volatile
    var uiAttached: Boolean = false

    override suspend fun confirm(request: ToolConfirmRequest): Boolean {
        if (coroutineContext[UnattendedToolCall] != null || !uiAttached) return false
        val id = ids.incrementAndGet()
        val deferred = CompletableDeferred<Boolean>()
        waiters[id] = deferred
        _pending.value = Pending(id, request.toolName, request.pluginName, request.detail)
        if (!uiAttached || coroutineContext[UnattendedToolCall] != null) {
            waiters.remove(id)
            if (_pending.value?.id == id) _pending.value = null
            return false
        }
        return try {
            deferred.await()
        } finally {
            waiters.remove(id)
            if (_pending.value?.id == id) _pending.value = null
        }
    }

    fun answer(allow: Boolean) {
        val current = _pending.value ?: return
        waiters[current.id]?.complete(allow)
    }
}

private val READ_WORDS = listOf(
    "get", "list", "search", "read", "find", "fetch", "query", "lookup", "describe", "status"
)
private val WRITE_WORDS = listOf(
    "create", "update", "delete", "send", "post", "write", "add", "remove", "set", "insert",
    "patch", "move", "archive", "trash", "publish", "deploy", "append", "edit"
)
