package dev.chungjungsoo.gptmobile.data.bot

import dev.chungjungsoo.gptmobile.data.agent.tool.AgentToolResolver
import dev.chungjungsoo.gptmobile.data.agent.tool.ResolvedAgentTool
import javax.inject.Inject

interface BotCompletionTools {
    suspend fun resolve(botUid: String): List<ResolvedAgentTool>?

    object PlatformOnly : BotCompletionTools {
        override suspend fun resolve(botUid: String): List<ResolvedAgentTool>? = null
    }
}

class BotToolResolver @Inject constructor(
    private val botRepository: BotRepository,
    private val agentToolResolver: AgentToolResolver
) : BotCompletionTools {
    override suspend fun resolve(botUid: String): List<ResolvedAgentTool> {
        val bindings = botRepository.listBindingsWithConnections(botUid)
        return agentToolResolver.resolveBindings(bindings, includeCurrentDate = false)
    }
}
