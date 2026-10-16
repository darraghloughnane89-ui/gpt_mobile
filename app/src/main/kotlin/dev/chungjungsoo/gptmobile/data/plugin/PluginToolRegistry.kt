package dev.chungjungsoo.gptmobile.data.plugin

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.tool.McpClientManager
import dev.chungjungsoo.gptmobile.data.agent.tool.ResolvedAgentTool
import dev.chungjungsoo.gptmobile.data.agent.tool.mapMcpToolResult
import dev.chungjungsoo.gptmobile.data.database.dao.PluginRoutineDao
import dev.chungjungsoo.gptmobile.data.database.entity.PluginEntity
import dev.chungjungsoo.gptmobile.data.database.entity.PluginToolEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class PluginHermesBridge @Inject constructor(
    private val dao: PluginRoutineDao,
    private val repository: PluginRepository,
    private val client: McpClientManager
) : HermesBridge {
    override suspend fun delegate(task: String): String {
        val plugin = dao.enabledPlugins().firstOrNull { it.catalogId == PluginCatalog.HERMES_ID }
            ?: return "Hermes is not enabled. Open Plugins, set the bridge URL, and enable Hermes."
        val result = client.callTool(
            repository.endpoint(plugin),
            PluginCatalog.HERMES_DELEGATE_TOOL,
            buildJsonObject {
                put("task", task)
                put("profile", PluginCatalog.HERMES_PROFILE)
            }
        )
        val text = result.content.filterIsInstance<io.modelcontextprotocol.kotlin.sdk.types.TextContent>()
            .joinToString("\n") { it.text }
        if (result.isError == true) return "Hermes returned an error: $text"
        return text.ifBlank { "Hermes returned an empty reply." }
    }
}

@Singleton
class PluginToolRegistry @Inject constructor(
    private val dao: PluginRoutineDao,
    private val repository: PluginRepository,
    private val client: McpClientManager,
    private val prompter: ToolConfirmBroker,
    private val targets: AgentTargetSource,
    private val handoff: AgentHandoffExecutor
) : ToolRegistry {
    override suspend fun toolsFor(scope: ToolScope): List<ResolvedAgentTool> {
        val plugins = dao.enabledPlugins()
        val enabledIds = plugins.map { it.pluginUid }.toSet()
        val selections = dao.listSelections().map {
            PluginScopeRef(it.scopeType, it.scopeId, it.pluginUid)
        }
        val chosen = pluginsForScope(enabledIds, selections, scope)
        val resolved = plugins.filter { it.pluginUid in chosen }.flatMap { plugin ->
            dao.listTools(plugin.pluginUid).mapNotNull { tool ->
                if (!tool.enabled) return@mapNotNull null
                tool.toResolved(plugin, client, repository, prompter)
            }
        }.toMutableList()
        if (scope.includeHandoff) {
            val delegate = delegateTool(handoff, targets)
            resolved += ResolvedAgentTool(
                tool = delegate,
                connectionUid = null,
                connectionName = "Agents",
                realToolName = DELEGATE_TOOL_NAME,
                modelToolName = DELEGATE_TOOL_NAME
            )
        }
        return resolved.distinctBy { it.modelToolName }
    }
}

private fun PluginToolEntity.toResolved(
    plugin: PluginEntity,
    client: McpClientManager,
    repository: PluginRepository,
    prompter: ToolConfirmPrompter
): ResolvedAgentTool {
    val schema = runCatching {
        Json.parseToJsonElement(inputSchemaJson) as JsonObject
    }.getOrElse { buildJsonObject { put("type", "object") } }
    val remote = PluginRemoteTool(
        definition = AgentToolDefinition(
            name = modelName,
            description = description.ifBlank { name },
            inputSchema = schema
        ),
        remoteName = name,
        pluginUid = plugin.pluginUid,
        client = client,
        repository = repository
    )
    val effect = runCatching { ToolEffect.valueOf(effect) }.getOrDefault(ToolEffect.WRITE)
    val gated = GatedAgentTool(
        definition = remote.definition,
        effect = effect,
        enabled = enabled,
        pluginName = plugin.name,
        prompter = prompter,
        delegate = remote
    )
    return ResolvedAgentTool(
        tool = gated,
        connectionUid = plugin.pluginUid,
        connectionName = plugin.name,
        realToolName = name,
        modelToolName = modelName
    )
}

private class PluginRemoteTool(
    override val definition: AgentToolDefinition,
    private val remoteName: String,
    private val pluginUid: String,
    private val client: McpClientManager,
    private val repository: PluginRepository
) : AgentTool {
    override suspend fun execute(callId: String, arguments: JsonObject): dev.chungjungsoo.gptmobile.data.agent.AgentToolResult {
        val plugin = repository.listPlugins().firstOrNull { it.pluginUid == pluginUid }
            ?: return dev.chungjungsoo.gptmobile.data.agent.AgentToolResult(
                callId,
                dev.chungjungsoo.gptmobile.data.agent.ToolResultContent.Text("Plugin is no longer installed."),
                true
            )
        return mapMcpToolResult(callId, client.callTool(repository.endpoint(plugin), remoteName, arguments))
    }
}
