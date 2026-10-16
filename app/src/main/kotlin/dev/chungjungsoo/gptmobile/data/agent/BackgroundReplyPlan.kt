package dev.chungjungsoo.gptmobile.data.agent

import dev.chungjungsoo.gptmobile.data.context.groupPersistedConversation
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunDraft
import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnRequest
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnResult
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.instruction.ChatCompletionContext
import java.util.UUID

internal data class BackgroundReplyPlan(
    val persist: PersistAgentTurnRequest,
    val platforms: List<PlatformV2>,
    val priorUsers: List<MessageV2>,
    val priorAssistants: List<List<MessageV2>>,
    val completion: ChatCompletionContext
)

internal fun planBackgroundReply(
    chat: ChatRoomV2,
    history: List<MessageV2>,
    platforms: List<PlatformV2>,
    chatPlatformModels: Map<String, String>,
    bot: Bot?,
    replyText: String,
    nowSeconds: Long,
    runIds: () -> String = { UUID.randomUUID().toString() }
): BackgroundReplyPlan? {
    val text = replyText.trim().takeIf { it.isNotEmpty() } ?: return null
    if (chat.id <= 0) return null
    val required = chat.enabledPlatform.filter { it.isNotBlank() }
    if (required.isEmpty()) return null
    val configured = platforms.filter { it.enabled }.associateBy(PlatformV2::uid)
    if (required.any { it !in configured }) return null
    val resolved = required.map { uid ->
        val platform = configured.getValue(uid)
        val model = chatPlatformModels[uid]?.trim().orEmpty()
        if (model.isBlank() || model == platform.model) platform else platform.copy(model = model)
    }
    val (priorUsers, priorAssistants) = groupPersistedConversation(history)
    val runs = resolved.map { platform ->
        AgentRunDraft(
            runId = runIds(),
            profileUid = platform.uid,
            providerSnapshot = platform.compatibleType.name,
            modelSnapshot = platform.model,
            createdAt = nowSeconds
        )
    }
    return BackgroundReplyPlan(
        persist = PersistAgentTurnRequest(
            chatRoom = chat.copy(updatedAt = nowSeconds),
            userMessage = MessageV2(
                chatId = chat.id,
                content = text,
                platformType = null,
                createdAt = nowSeconds
            ),
            runs = runs,
            chatPlatformModels = chatPlatformModels.filterKeys { it in required }
        ),
        platforms = resolved,
        priorUsers = priorUsers,
        priorAssistants = priorAssistants,
        completion = ChatCompletionContext(
            chatId = chat.id,
            botUid = bot?.botUid,
            botInstructions = bot?.instructions
        )
    )
}

internal fun requestsForPersistedReply(
    plan: BackgroundReplyPlan,
    persisted: PersistAgentTurnResult
): List<AgentRunRequest> {
    if (persisted.assistantMessages.size != plan.platforms.size || plan.persist.runs.size != plan.platforms.size) {
        return emptyList()
    }
    val users = plan.priorUsers + persisted.userMessage
    val assistants = plan.priorAssistants + listOf(persisted.assistantMessages)
    return plan.platforms.mapIndexed { index, platform ->
        AgentRunRequest(
            runId = plan.persist.runs[index].runId,
            chatId = persisted.chatRoom.id,
            assistantMessage = persisted.assistantMessages[index],
            platform = platform,
            userMessages = users,
            assistantMessages = assistants,
            completion = plan.completion
        )
    }
}
