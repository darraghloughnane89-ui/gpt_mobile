package dev.chungjungsoo.gptmobile.data.skill

import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.instruction.InstructionContext
import dev.chungjungsoo.gptmobile.data.instruction.InstructionStack
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only collaborator chat needs. It parses slash commands, builds the skill
 * slice of the system prompt, and runs the learning loop after a chat settles.
 */
@Singleton
class ChatSkillBridge @Inject constructor(
    private val repository: SkillRepository,
    private val learningLoop: LearningLoop,
    private val stack: InstructionStack,
    private val chatRepository: ChatRepository
) {
    suspend fun skillBlock(userText: String, manualNames: List<String>, botUid: String?): SkillBlock {
        val composition = stack.compose(
            InstructionContext(
                botUid = botUid,
                userText = userText,
                manualSkillNames = manualNames
            )
        )
        val segment = composition.segments.firstOrNull { it.key == SkillInstructionProvider.KEY }
        return SkillBlock(
            text = segment?.text.orEmpty(),
            skillIds = segment?.skillIds.orEmpty()
        )
    }

    fun applyBlock(platform: PlatformV2, block: String): PlatformV2 {
        if (block.isBlank()) return platform
        return platform.copy(systemPrompt = InstructionStack.merge(platform.systemPrompt, block))
    }

    suspend fun parse(text: String): SkillSlashParser.Result {
        val names = repository.listSkills().map { it.stableName }.toSet()
        return SkillSlashParser.parse(text, names)
    }

    suspend fun recordUse(ids: List<Long>) {
        if (ids.isNotEmpty()) repository.recordUse(ids)
    }

    suspend fun recordFeedback(ids: List<Long>, up: Boolean) {
        if (ids.isNotEmpty()) repository.recordFeedback(ids, up)
    }

    suspend fun summaries(): List<SkillSummary> = repository.summaries()

    suspend fun reviewAfterChat(chatId: Int, fallback: List<LearningTurn>, explicit: Boolean): LearningResult {
        val turns = if (chatId > 0) {
            SkillTranscript.fromPersisted(chatRepository.fetchMessagesV2(chatId))
        } else {
            fallback
        }
        return learningLoop.review(LearningRequest(chatId, turns.ifEmpty { fallback }, explicit))
    }
}

data class SkillBlock(
    val text: String,
    val skillIds: List<Long>
)

fun skillTurns(users: List<MessageV2>, assistants: List<List<MessageV2>>): List<LearningTurn> =
    SkillTranscript.fromMessages(users, assistants)
