package dev.chungjungsoo.gptmobile.data.instruction

/**
 * Orders instruction slices the way stream (b) described them:
 * platform prompt, project instructions, bot instructions, then skills.
 * Stream (b) can register earlier providers. This stack appends whatever it is given.
 */
object InstructionOrder {
    const val PLATFORM = 0
    const val PROJECT = 10
    const val BOT = 20
    const val SKILLS = 40
}

data class InstructionContext(
    val chatId: Int = 0,
    val botUid: String? = null,
    val platformUid: String? = null,
    val userText: String = "",
    val manualSkillNames: List<String> = emptyList(),
    val basePrompt: String? = null
)

data class InstructionSegment(
    val key: String,
    val text: String,
    val skillIds: List<Long> = emptyList()
)

data class InstructionComposition(
    val text: String,
    val segments: List<InstructionSegment>
)

/**
 * Supplies one slice of the system prompt. [SkillInstructionProvider] is the skills slice.
 */
interface InstructionProvider {
    val key: String
    val order: Int

    suspend fun provide(context: InstructionContext): InstructionSegment?
}

class InstructionStack(
    private val providers: List<InstructionProvider>
) {
    suspend fun compose(context: InstructionContext): InstructionComposition {
        val segments = providers
            .sortedBy { it.order }
            .mapNotNull { provider -> provider.provide(context) }
            .filter { it.text.isNotBlank() }
        val extra = segments.joinToString("\n\n") { it.text.trim() }
        return InstructionComposition(
            text = merge(context.basePrompt, extra),
            segments = segments
        )
    }

    companion object {
        fun merge(base: String?, extra: String?): String {
            val left = base?.trim().orEmpty()
            val right = extra?.trim().orEmpty()
            return when {
                left.isEmpty() -> right
                right.isEmpty() -> left
                else -> "$left\n\n$right"
            }
        }
    }
}
