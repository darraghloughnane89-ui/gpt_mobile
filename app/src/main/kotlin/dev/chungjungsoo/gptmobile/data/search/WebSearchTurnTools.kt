package dev.chungjungsoo.gptmobile.data.search

import dev.chungjungsoo.gptmobile.data.agent.tool.FetchPageTool
import dev.chungjungsoo.gptmobile.data.agent.tool.FreeWebSearchTool
import dev.chungjungsoo.gptmobile.data.agent.tool.ResolvedAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2

private const val WEB_SEARCH = "web_search"
private const val FETCH_PAGE = "fetch_page"

fun augmentToolsForWebSearch(
    assigned: List<ResolvedAgentTool>,
    platform: PlatformV2,
    webSearchEnabled: Boolean,
    searxngUrl: String?
): List<ResolvedAgentTool> {
    if (!webSearchEnabled) return assigned
    val native = nativeWebSearchKind(platform) != null
    val kept = if (native) assigned.filterNot { it.modelToolName == WEB_SEARCH } else assigned
    val numberer = CitationNumberer()
    val extras = mutableListOf<ResolvedAgentTool>()
    if (!native && kept.none { it.modelToolName == WEB_SEARCH }) {
        extras += FreeWebSearchTool(FreeWebSearchClient(searxngUrl), numberer).asResolved()
    }
    if (kept.none { it.modelToolName == FETCH_PAGE }) {
        extras += FetchPageTool(numberer).asResolved()
    }
    return kept + extras
}

private fun dev.chungjungsoo.gptmobile.data.agent.AgentTool.asResolved(): ResolvedAgentTool = ResolvedAgentTool(
    tool = this,
    connectionUid = null,
    connectionName = null,
    realToolName = definition.name,
    modelToolName = definition.name
)
