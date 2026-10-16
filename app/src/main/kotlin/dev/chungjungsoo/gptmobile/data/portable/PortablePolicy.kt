package dev.chungjungsoo.gptmobile.data.portable

import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool

/**
 * What an imported document is allowed to turn on.
 * Web search and MCP stay off because they need a connection and a secret
 * that must never travel with the file. Skill tool names are kept as text
 * requirements and do not create a connection.
 */
object PortablePolicy {
    const val WEB_SEARCH = "web_search"
    const val MCP = "mcp"

    data class BotGrants(
        val requests: List<PortableToolRequest>,
        val currentDate: Boolean,
        val readUrl: Boolean
    )

    fun normalizeTool(raw: String): String {
        val key = raw.trim().lowercase().replace('-', '_').replace(' ', '_')
        val mapped = when (key) {
            "*", "mcp", "mcp_all", "mcp_server" -> MCP
            "websearch", "web_search" -> WEB_SEARCH
            "currentdate", "current_date" -> BuiltInAgentTool.CURRENT_DATE
            "readurl", "read_url" -> BuiltInAgentTool.READ_URL
            else -> key
        }
        return mapped.take(PortableLimits.MAX_TOOL_NAME)
    }

    fun cleanTools(raw: List<String>): List<String> = raw
        .map(::normalizeTool)
        .filter { it.isNotEmpty() }
        .distinct()
        .take(PortableLimits.MAX_TOOLS)

    fun botGrants(tools: List<String>): BotGrants {
        val names = cleanTools(tools)
        var currentDate = false
        var readUrl = false
        val requests = names.map { name ->
            when (name) {
                BuiltInAgentTool.CURRENT_DATE -> {
                    currentDate = true
                    PortableToolRequest(name, PortableToolDecision.ENABLE)
                }
                BuiltInAgentTool.READ_URL -> {
                    readUrl = true
                    PortableToolRequest(name, PortableToolDecision.ENABLE)
                }
                else -> PortableToolRequest(name, PortableToolDecision.WITHHOLD)
            }
        }
        return BotGrants(requests, currentDate, readUrl)
    }

    fun skillRequests(tools: List<String>): List<PortableToolRequest> =
        cleanTools(tools).map { PortableToolRequest(it, PortableToolDecision.RECORD_ONLY) }

    fun withheld(tools: List<String>): List<PortableToolRequest> =
        cleanTools(tools).map { PortableToolRequest(it, PortableToolDecision.WITHHOLD) }
}
