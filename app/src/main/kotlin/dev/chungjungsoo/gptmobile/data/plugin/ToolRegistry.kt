package dev.chungjungsoo.gptmobile.data.plugin

import dev.chungjungsoo.gptmobile.data.agent.tool.ResolvedAgentTool

data class ToolScope(
    val platformUid: String,
    val botUid: String? = null,
    val projectId: String? = null,
    val includeHandoff: Boolean = true,
    val pluginUidFilter: Set<String>? = null
)

interface ToolRegistry {
    suspend fun toolsFor(scope: ToolScope): List<ResolvedAgentTool>
}

object EmptyToolRegistry : ToolRegistry {
    override suspend fun toolsFor(scope: ToolScope): List<ResolvedAgentTool> = emptyList()
}

data class BotSummary(val uid: String, val name: String)

interface BotDirectory {
    suspend fun bots(): List<BotSummary>
}

class UnavailableBotDirectory @javax.inject.Inject constructor() : BotDirectory {
    override suspend fun bots(): List<BotSummary> = emptyList()
}

data class ProjectSummary(val id: String, val name: String)

interface ProjectDirectory {
    suspend fun projects(): List<ProjectSummary>
}

class UnavailableProjectDirectory @javax.inject.Inject constructor() : ProjectDirectory {
    override suspend fun projects(): List<ProjectSummary> = emptyList()
}

data class PluginScopeRef(
    val scopeType: String,
    val scopeId: String,
    val pluginUid: String
)

fun pluginsForScope(
    enabledPluginUids: Set<String>,
    selections: List<PluginScopeRef>,
    scope: ToolScope
): Set<String> {
    fun selected(type: String, id: String?): Set<String>? {
        if (id.isNullOrBlank()) return null
        val rows = selections.filter { it.scopeType == type && it.scopeId == id }
        if (rows.isEmpty()) return null
        return rows.map { it.pluginUid }.toSet()
    }
    val chosen = selected(PluginScopeType.BOT, scope.botUid)
        ?: selected(PluginScopeType.PROJECT, scope.projectId)
        ?: selected(PluginScopeType.PLATFORM, scope.platformUid)
        ?: enabledPluginUids
    val filtered = scope.pluginUidFilter?.let { filter -> chosen.intersect(filter) } ?: chosen
    return filtered.intersect(enabledPluginUids)
}
