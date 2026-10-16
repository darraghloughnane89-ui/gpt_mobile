package dev.chungjungsoo.gptmobile.data.phonecontrol

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.agent.provider.AnthropicMessagesAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.GeminiAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.LiteRtLmAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.OpenAICompatibleAdapter
import dev.chungjungsoo.gptmobile.data.agent.provider.OpenAIResponsesAdapter
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.localruntime.LocalRuntime
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.ActionPlannerParser
import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.PhoneControlPrompts
import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.PlannedAction
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.Redaction
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.SecretMemoryPolicy
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.LocalModelRepository
import dev.chungjungsoo.gptmobile.data.repository.ModelCatalogRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.di.DeviceSocModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject

data class PhonePlan(
    val actions: List<PlannedAction>,
    val error: String?
)

@Singleton
class PhoneControlModelPlanner @Inject constructor(
    private val settingRepository: SettingRepository,
    private val chatRepository: ChatRepository,
    private val openAIResponsesAdapter: OpenAIResponsesAdapter,
    private val openAICompatibleAdapter: OpenAICompatibleAdapter,
    private val anthropicMessagesAdapter: AnthropicMessagesAdapter,
    private val geminiAdapter: GeminiAdapter,
    localRuntime: LocalRuntime,
    localModelRepository: LocalModelRepository,
    modelCatalogRepository: ModelCatalogRepository,
    @param:DeviceSocModel private val deviceSocModel: String
) {
    private val liteRtLmAdapter = LiteRtLmAdapter(
        localRuntime = localRuntime,
        localModelRepository = localModelRepository,
        ignoredAttachmentsNotice = LiteRtLmAdapter.DEFAULT_IGNORED_ATTACHMENTS,
        modelNotDownloadedError = LiteRtLmAdapter.DEFAULT_MODEL_NOT_DOWNLOADED,
        modelCatalogRepository = modelCatalogRepository,
        deviceSocModel = deviceSocModel
    )
    private val queuedTools = PhoneControlPrompts.definitions().map(::QueuedPhoneTool)
    suspend fun plan(chatRoomId: Int, goal: String, observation: PhoneObservation, history: List<String>): PhonePlan {
        val platform = resolvePlatform(chatRoomId) ?: return PhonePlan(emptyList(), "Model offline or unavailable. Switch or retry.")
        val prompt = buildString {
            append(PhoneControlPrompts.SYSTEM.trim())
            append("\n\nGoal: ")
            append(Redaction.redactSecrets(goal))
            append("\n\n")
            append(observation.treeText)
            if (history.isNotEmpty()) {
                append("\n\nHistory:\n")
                append(history.joinToString("\n") { SecretMemoryPolicy.admit(it) ?: Redaction.redactSecrets(it) })
            }
        }
        val turn = ConversationTurn(
            userMessage = MessageV2(content = prompt, platformType = null),
            assistantMessage = null,
            isCurrentTurn = true
        )
        return runCatching {
            val session = openSession(platform, listOf(turn))
            val text = StringBuilder()
            val calls = mutableListOf<ProviderEvent.ToolCall>()
            session.streamRound(PhoneControlPrompts.definitions(), emptyList()).collect { event ->
                when (event) {
                    is ProviderEvent.TextDelta -> text.append(event.text)
                    is ProviderEvent.ToolCall -> calls += event
                    is ProviderEvent.Failed -> error(event.message)
                    else -> Unit
                }
            }
            val fromTools = calls.mapNotNull { ActionPlannerParser.fromToolCall(it.name, it.arguments) }
            val fromText = ActionPlannerParser.parseModelOutput(text.toString())
            PhonePlan(fromTools + fromText, null)
        }.getOrElse { error ->
            PhonePlan(emptyList(), error.message ?: "Couldn’t reach the model. Try again — the wind’s a bit gusty.")
        }
    }

    private suspend fun resolvePlatform(chatRoomId: Int): PlatformV2? {
        val platforms = settingRepository.fetchPlatformV2s().filter { it.enabled }
        if (platforms.isEmpty()) return null
        if (chatRoomId <= 0) return platforms.first()
        val room = chatRepository.fetchChatListV2().firstOrNull { it.id == chatRoomId } ?: return platforms.first()
        val uid = room.enabledPlatformIds().firstOrNull() ?: return platforms.first()
        val platform = platforms.firstOrNull { it.uid == uid } ?: return platforms.first()
        val override = chatRepository.fetchChatPlatformModels(chatRoomId)[uid]
        return if (override.isNullOrBlank()) platform else platform.copy(model = override)
    }

    private suspend fun openSession(platform: PlatformV2, turns: List<ConversationTurn>) = when (platform.compatibleType) {
        ClientType.OPENAI -> openAIResponsesAdapter.openSession(turns, platform.copy(systemPrompt = PhoneControlPrompts.SYSTEM.trim()))
        ClientType.GROQ, ClientType.OLLAMA, ClientType.OPENROUTER, ClientType.CUSTOM ->
            openAICompatibleAdapter.openSession(turns, platform.copy(systemPrompt = PhoneControlPrompts.SYSTEM.trim()))
        ClientType.ANTHROPIC -> anthropicMessagesAdapter.openSession(turns, platform.copy(systemPrompt = PhoneControlPrompts.SYSTEM.trim()))
        ClientType.GOOGLE -> geminiAdapter.openSession(turns, platform.copy(systemPrompt = PhoneControlPrompts.SYSTEM.trim()))
        ClientType.LITERT_LM -> liteRtLmAdapter.openSession(
            turns,
            platform.copy(systemPrompt = PhoneControlPrompts.SYSTEM.trim()),
            queuedTools
        )
    }

    private fun ChatRoomV2.enabledPlatformIds(): List<String> = enabledPlatform.filter { it.isNotBlank() }
}

private class QueuedPhoneTool(
    override val definition: dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
) : AgentTool {
    override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult = AgentToolResult(
        callId = callId,
        content = ToolResultContent.Text("queued for confirmation"),
        isError = false
    )
}
