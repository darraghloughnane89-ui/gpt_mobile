package dev.chungjungsoo.gptmobile.data.instruction

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2

/**
 * Ordered system-prompt layers for one completion.
 *
 * Composition order is fixed so later streams can add a layer without
 * redefining the prompt:
 * 1. [global] — today this is the Platform system prompt
 * 2. [project] — shared project instructions, null until that stream supplies them
 * 3. [bot] — the selected bot's persona
 * 4. [chat] — instructions that apply only to this chat
 *
 * Blank layers are skipped. A single remaining layer is returned unchanged,
 * so a Platform prompt with no project, bot, or chat text stays byte-identical.
 * Two or more layers are joined with a blank line. Global text is unlabeled.
 * The other layers are labeled [PROJECT_LABEL], [BOT_LABEL], and [CHAT_LABEL].
 */
data class InstructionStack(
    val global: String? = null,
    val project: String? = null,
    val bot: String? = null,
    val chat: String? = null
) {
    fun compose(): String {
        val present = listOf(
            Present(label = null, text = global),
            Present(label = PROJECT_LABEL, text = project),
            Present(label = BOT_LABEL, text = bot),
            Present(label = CHAT_LABEL, text = chat)
        ).mapNotNull { layer ->
            val text = layer.text?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PresentText(label = layer.label, text = text)
        }
        if (present.isEmpty()) return ""
        if (present.size == 1) return present.single().text
        return present.joinToString(separator = "\n\n") { layer ->
            val body = layer.text.trim()
            if (layer.label == null) body else "${layer.label}:\n$body"
        }
    }

    private data class Present(
        val label: String?,
        val text: String?
    )

    private data class PresentText(
        val label: String?,
        val text: String
    )

    companion object {
        const val PROJECT_LABEL = "Project instructions"
        const val BOT_LABEL = "Bot instructions"
        const val CHAT_LABEL = "Chat instructions"
    }
}

/**
 * Project instructions for a chat. The projects stream replaces the Hilt binding.
 * [NullProjectInstructionSource] is the placeholder and always returns null.
 */
fun interface ProjectInstructionSource {
    suspend fun instructionsFor(chatId: Int): String?
}

object NullProjectInstructionSource : ProjectInstructionSource {
    override suspend fun instructionsFor(chatId: Int): String? = null
}

class InstructionStackComposer(
    private val projectInstructions: ProjectInstructionSource
) {
    suspend fun forChat(
        chatId: Int,
        global: String?,
        bot: String?,
        chat: String?
    ): InstructionStack = InstructionStack(
        global = global,
        project = projectInstructions.instructionsFor(chatId),
        bot = bot,
        chat = chat
    )
}

/**
 * Per-run instructions and the bot whose tool bindings should answer.
 * A null [botUid] keeps the Platform's own tool bindings.
 */
data class ChatCompletionContext(
    val chatId: Int = 0,
    val botUid: String? = null,
    val botInstructions: String? = null,
    val chatInstructions: String? = null,
    val imageGenerationEnabled: Boolean = false,
    val imageStyle: String? = null
)

fun PlatformV2.withInstructionStack(stack: InstructionStack): PlatformV2 {
    val composed = stack.compose()
    if (composed == systemPrompt.orEmpty()) return this
    if (composed.isEmpty() && systemPrompt.isNullOrBlank()) return this
    return copy(systemPrompt = composed.ifEmpty { null })
}
