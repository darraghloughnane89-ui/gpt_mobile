package dev.chungjungsoo.gptmobile.data.context

/**
 * Stub for the bots stream's instruction stack.
 *
 * Layer order is global, then project, then bot, then chat.
 * Stream (b) owns this type. Keep the constructor and [composed] stable so a rebase stays small.
 * The project layer is filled by [dev.chungjungsoo.gptmobile.data.project.ProjectContextProvider].
 */
data class InstructionStack(
    val global: String = "",
    val project: String = "",
    val bot: String = "",
    val chat: String = ""
) {
    fun composed(): String = listOf(global, project, bot, chat)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")

    companion object {
        fun compose(
            global: String? = null,
            project: String? = null,
            bot: String? = null,
            chat: String? = null
        ): InstructionStack = InstructionStack(
            global = global.orEmpty(),
            project = project.orEmpty(),
            bot = bot.orEmpty(),
            chat = chat.orEmpty()
        )
    }
}
