package dev.chungjungsoo.gptmobile.presentation.fastentry

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.agent.AgentRunCoordinator
import dev.chungjungsoo.gptmobile.data.agent.AgentRunRequest
import dev.chungjungsoo.gptmobile.data.bot.BotRepository
import dev.chungjungsoo.gptmobile.data.context.groupPersistedConversation
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunDraft
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRunStatus
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnRequest
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.fastentry.FastEntryPlanner
import dev.chungjungsoo.gptmobile.data.instruction.ChatCompletionContext
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuickAskUiState(
    val draft: String = "",
    val answer: String = "",
    val phase: FastEntryPlanner.Phase = FastEntryPlanner.Phase.IDLE,
    val error: String? = null,
    val chatId: Int = 0,
    val platforms: List<String> = emptyList(),
    val canReplaceSelection: Boolean = false
)

@HiltViewModel
class QuickAskViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val settingRepository: SettingRepository,
    private val botRepository: BotRepository,
    private val agentRunCoordinator: AgentRunCoordinator,
    private val shortcuts: FastEntryShortcutPublisher
) : ViewModel() {
    private val _state = MutableStateFlow(QuickAskUiState())
    val state = _state.asStateFlow()

    private var launchBound = false
    private var sendJob: Job? = null

    fun bindLaunch(selection: String?, canReplaceSelection: Boolean) {
        if (launchBound) return
        launchBound = true
        _state.update { current ->
            current.copy(
                draft = selection?.takeIf { it.isNotBlank() } ?: current.draft,
                canReplaceSelection = canReplaceSelection
            )
        }
    }

    fun offerSelection(selection: String?, canReplaceSelection: Boolean) {
        _state.update { current ->
            if (current.phase == FastEntryPlanner.Phase.SENDING) return@update current
            current.copy(
                draft = selection?.takeIf { it.isNotBlank() } ?: current.draft,
                canReplaceSelection = canReplaceSelection,
                answer = "",
                phase = FastEntryPlanner.Phase.IDLE,
                error = null,
                chatId = 0,
                platforms = emptyList()
            )
        }
    }

    fun updateDraft(value: String) {
        _state.update { current ->
            if (current.phase == FastEntryPlanner.Phase.SENDING) current else current.copy(draft = value, error = null)
        }
    }

    fun send() {
        val snapshot = _state.value
        val question = snapshot.draft.trim()
        if (question.isEmpty() || snapshot.phase == FastEntryPlanner.Phase.SENDING) return
        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            _state.update {
                it.copy(phase = FastEntryPlanner.Phase.SENDING, error = null, answer = "", draft = question)
            }
            try {
                val turn = persistTurn(question, snapshot.chatId)
                _state.update {
                    it.copy(
                        draft = "",
                        chatId = turn.chatId,
                        platforms = turn.platforms
                    )
                }
                shortcuts.publishAsync()
                agentRunCoordinator.start(listOf(turn.request))
                watch(turn.chatId, turn.request.runId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        phase = FastEntryPlanner.Phase.FAILED,
                        draft = question,
                        error = error.message?.takeIf { message -> message.isNotBlank() }
                            ?: context.getString(R.string.quick_ask_failed)
                    )
                }
            }
        }
    }

    private suspend fun persistTurn(question: String, existingChatId: Int): PreparedTurn {
        val enabled = settingRepository.fetchPlatformV2s().filter { it.enabled }
        val existing = existingChatId.takeIf { it > 0 }?.let { chatId ->
            chatRepository.fetchChatListV2().firstOrNull { it.id == chatId }
        }
        val bot = resolveBot(existing?.botUid)
        val pool = if (existing != null) {
            enabled.filter { it.uid in existing.enabledPlatform }.ifEmpty { enabled }
        } else {
            enabled
        }
        val choice = FastEntryPlanner.pickPlatform(
            pool.map { FastEntryPlanner.PlatformChoice(it.uid, it.model) },
            bot?.defaultPlatformUid
        ) ?: throw IllegalStateException(context.getString(R.string.fast_entry_no_platform))
        val platform = pool.first { it.uid == choice.uid }
        val savedModel = existing?.let { chatRepository.fetchChatPlatformModels(it.id)[platform.uid] }
        val model = FastEntryPlanner.modelFor(
            choice,
            savedModel ?: bot?.modelOverride(setOf(platform.uid))?.second
        )
        val resolved = if (model == platform.model) platform else platform.copy(model = model)
        val now = System.currentTimeMillis() / 1000
        val runId = UUID.randomUUID().toString()
        val chatRoom = existing?.copy(updatedAt = now) ?: ChatRoomV2(
            title = question.replace('\n', ' ').take(50),
            enabledPlatform = listOf(resolved.uid),
            botUid = bot?.botUid,
            createdAt = now,
            updatedAt = now
        )
        val persisted = chatRepository.persistAgentTurn(
            PersistAgentTurnRequest(
                chatRoom = chatRoom,
                userMessage = MessageV2(content = question, platformType = null, createdAt = now),
                runs = listOf(
                    AgentRunDraft(
                        runId = runId,
                        profileUid = resolved.uid,
                        providerSnapshot = resolved.compatibleType.name,
                        modelSnapshot = resolved.model,
                        createdAt = now
                    )
                ),
                chatPlatformModels = mapOf(resolved.uid to resolved.model)
            )
        )
        val assistant = persisted.assistantMessages.first()
        val grouped = groupPersistedConversation(chatRepository.fetchMessagesV2(persisted.chatRoom.id))
        val request = AgentRunRequest(
            runId = runId,
            chatId = persisted.chatRoom.id,
            assistantMessage = assistant,
            platform = resolved,
            userMessages = grouped.first,
            assistantMessages = grouped.second,
            completion = ChatCompletionContext(
                chatId = persisted.chatRoom.id,
                botUid = bot?.botUid,
                botInstructions = bot?.instructions
            )
        )
        return PreparedTurn(
            chatId = persisted.chatRoom.id,
            platforms = persisted.chatRoom.enabledPlatform,
            request = request
        )
    }

    private suspend fun resolveBot(chatBotUid: String?) = when {
        !chatBotUid.isNullOrBlank() -> botRepository.getBot(chatBotUid)
        else -> botRepository.lastSelectedBotUid()?.let { botRepository.getBot(it) }
    }

    private suspend fun watch(chatId: Int, runId: String) {
        val terminal = coroutineScope {
            val messages = launch {
                chatRepository.observeMessagesV2(chatId).collect { list ->
                    val text = list.lastOrNull { it.platformType != null }?.effectiveContent().orEmpty()
                    if (text.isNotEmpty()) publishAnswer(text)
                }
            }
            val live = launch {
                agentRunCoordinator.liveSnapshots.collect { snapshots ->
                    val text = snapshots[runId]?.content.orEmpty()
                    if (text.isNotEmpty()) publishAnswer(text)
                }
            }
            val status = chatRepository.observeAgentRuns(chatId)
                .first { runs -> runs.any { it.runId == runId && it.status in TERMINAL } }
                .first { it.runId == runId }
                .status
            messages.cancel()
            live.cancel()
            status
        }
        val persisted = chatRepository.fetchMessagesV2(chatId)
            .lastOrNull { it.platformType != null }
            ?.effectiveContent()
            .orEmpty()
        val answer = FastEntryPlanner.preferAnswer(_state.value.answer, persisted)
        val failed = terminal != AgentRunStatus.COMPLETED
        _state.update { current ->
            current.copy(
                answer = answer,
                phase = FastEntryPlanner.phaseFor(terminal),
                error = if (failed && answer.isBlank()) context.getString(R.string.quick_ask_failed) else null
            )
        }
    }

    private fun publishAnswer(text: String) {
        _state.update { current ->
            if (current.phase != FastEntryPlanner.Phase.SENDING) {
                current
            } else {
                current.copy(answer = FastEntryPlanner.preferAnswer(current.answer, text))
            }
        }
    }

    private data class PreparedTurn(
        val chatId: Int,
        val platforms: List<String>,
        val request: AgentRunRequest
    )

    private companion object {
        val TERMINAL = setOf(
            AgentRunStatus.COMPLETED,
            AgentRunStatus.FAILED,
            AgentRunStatus.CANCELED,
            AgentRunStatus.INTERRUPTED
        )
    }
}
