package dev.chungjungsoo.gptmobile.data.plugin

import dagger.Lazy
import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.ApiState
import dev.chungjungsoo.gptmobile.data.hermes.HermesDefaults
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

const val HANDOFF_RUN_PREFIX = "handoff-"
const val DELEGATE_TOOL_NAME = "delegate_task"

data class AgentTargetRef(val id: String, val label: String)

interface AgentTargetSource {
    suspend fun list(): List<AgentTargetRef>
    suspend fun accepts(targetId: String): Boolean
}

fun interface AgentReplySource {
    suspend fun reply(targetId: String, task: String): String
}

fun interface HermesBridge {
    suspend fun delegate(task: String): String
}

class AgentHandoffExecutor(
    private val targets: AgentTargetSource,
    private val replies: AgentReplySource,
    private val hermes: HermesBridge
) {
    suspend fun handoff(targetId: String, task: String): String {
        val trimmed = task.trim()
        if (trimmed.isEmpty()) return "Handoff failed: the task is empty."
        if (targetId.equals(PluginCatalog.HERMES_TARGET, ignoreCase = true)) {
            return runCatching { hermes.delegate(trimmed) }
                .getOrElse { error -> "Handoff to Hermes failed: ${error.message ?: "unknown error"}" }
        }
        if (!targets.accepts(targetId)) {
            val known = targets.list().joinToString { "${it.label} (${it.id})" }
            return "Handoff failed: no bot or platform matches '$targetId'. Available: ${known.ifBlank { "none yet" }}."
        }
        return runCatching { replies.reply(targetId, trimmed) }
            .getOrElse { error -> "Handoff failed: ${error.message ?: "unknown error"}" }
    }
}

class InstalledAgentTargetSource @Inject constructor(
    private val platforms: PlatformV2Dao,
    private val bots: BotDirectory
) : AgentTargetSource {
    override suspend fun list(): List<AgentTargetRef> {
        val platformTargets = platforms.getPlatforms().map { AgentTargetRef(it.uid, it.name) }
        val botTargets = bots.bots().map { AgentTargetRef(it.uid, it.name) }
        return platformTargets + botTargets
    }

    override suspend fun accepts(targetId: String): Boolean = list().any { it.id == targetId || it.label.equals(targetId, ignoreCase = true) }
}

class ChatAgentReplySource @Inject constructor(
    private val platforms: PlatformV2Dao,
    private val chatRepository: Lazy<ChatRepository>
) : AgentReplySource {
    override suspend fun reply(targetId: String, task: String): String {
        val platform = platforms.getPlatforms().firstOrNull { it.uid == targetId || it.name.equals(targetId, ignoreCase = true) }
            ?: throw IllegalArgumentException("Platform '$targetId' was not found.")
        return collectReply(platform, task)
    }

    private suspend fun collectReply(platform: PlatformV2, task: String): String {
        val text = StringBuilder()
        var error: String? = null
        chatRepository.get().completeChat(
            userMessages = listOf(MessageV2(content = task, platformType = null)),
            assistantMessages = emptyList(),
            platform = platform,
            runId = HANDOFF_RUN_PREFIX + UUID.randomUUID()
        ).collect { state ->
            when (state) {
                is ApiState.Success -> text.append(state.textChunk)
                is ApiState.Error -> error = state.message
                else -> Unit
            }
        }
        error?.let { throw IllegalStateException(it) }
        return text.toString().ifBlank { "The other agent returned an empty reply." }
    }
}

class DelegateTaskTool(
    private val executor: AgentHandoffExecutor,
    override val definition: AgentToolDefinition,
    private val prompter: ToolConfirmPrompter = AllowToolConfirmPrompter
) : AgentTool {
    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
        if (coroutineContext[UnattendedToolCall] != null) {
            return AgentToolResult(
                callId = callId,
                content = ToolResultContent.Text("Handoff was blocked because this run is unattended."),
                isError = true
            )
        }
        val allowed = prompter.confirm(
            ToolConfirmRequest(
                toolName = definition.name,
                pluginName = "Agents",
                detail = "Hand this task to another agent?"
            )
        )
        if (!allowed) {
            return AgentToolResult(
                callId = callId,
                content = ToolResultContent.Text("Handoff was not confirmed."),
                isError = true
            )
        }
        val target = (arguments["target_id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val task = (arguments["task"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val reply = executor.handoff(target, task)
        val failed = reply.startsWith("Handoff failed")
        return AgentToolResult(
            callId = callId,
            content = ToolResultContent.Text(reply),
            isError = failed
        )
    }
}

suspend fun delegateTool(
    executor: AgentHandoffExecutor,
    targets: AgentTargetSource,
    prompter: ToolConfirmPrompter = AllowToolConfirmPrompter
): DelegateTaskTool {
    val listed = targets.list().joinToString { "${it.label} (${it.id})" }
    val description = "Hand a task to another bot or platform and wait for the reply. " +
        "Use target_id ${PluginCatalog.HERMES_TARGET} for Hermes. Hermes uses the API server saved in Settings → Hermes " +
        "(http://<tailscale-ip>:8642, Bearer API key, profile ${HermesDefaults.PROFILE}), not an MCP plugin. " +
        "Other targets: ${listed.ifBlank { "none saved yet" }}."
    return DelegateTaskTool(
        executor = executor,
        prompter = prompter,
        definition = AgentToolDefinition(
            name = DELEGATE_TOOL_NAME,
            description = description,
            inputSchema = buildJsonObject {
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "target_id",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "Platform uid, platform name, bot id, or hermes")
                            }
                        )
                        put(
                            "task",
                            buildJsonObject {
                                put("type", "string")
                                put("description", "What the other agent should do")
                            }
                        )
                    }
                )
                put(
                    "required",
                    buildJsonArray {
                        add(JsonPrimitive("target_id"))
                        add(JsonPrimitive("task"))
                    }
                )
            }
        )
    )
}
