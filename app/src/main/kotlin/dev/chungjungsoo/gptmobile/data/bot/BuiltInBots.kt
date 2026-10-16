package dev.chungjungsoo.gptmobile.data.bot

import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.data.database.entity.BotToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool

object BuiltInBots {
    const val ASSISTANT_UID = "builtin-assistant"
    const val CODER_UID = "builtin-coder"

    data class Definition(
        val bot: Bot,
        val bindings: List<BotToolBinding>
    )

    fun definitions(): List<Definition> = listOf(assistant(), coder())

    fun assistant(): Definition = definition(
        uid = ASSISTANT_UID,
        name = "Assistant",
        emoji = "✨",
        color = "#6750A4",
        instructions = "You are a helpful assistant. Be clear, accurate, and concise. " +
            "Ask one short clarifying question when the request is ambiguous.",
        starterPrompts = listOf(
            "Help me plan my day",
            "Explain this in simple terms",
            "Summarize the key points"
        )
    )

    fun coder(): Definition = definition(
        uid = CODER_UID,
        name = "Coder",
        emoji = "💻",
        color = "#006A6A",
        instructions = "You are a careful software engineer. Prefer small, correct changes. " +
            "Explain trade-offs briefly, show code when it helps, and call out risks or missing tests.",
        starterPrompts = listOf(
            "Review this code for bugs",
            "Write a unit test for this function",
            "Explain what this error means"
        )
    )

    private fun definition(
        uid: String,
        name: String,
        emoji: String,
        color: String,
        instructions: String,
        starterPrompts: List<String>
    ): Definition {
        val bot = Bot(
            botUid = uid,
            name = name,
            avatarEmoji = emoji,
            avatarColor = color,
            instructions = instructions,
            starterPrompts = StarterPrompts.encode(starterPrompts),
            builtin = true,
            createdAt = 0,
            updatedAt = 0
        )
        val bindings = listOf(BuiltInAgentTool.CURRENT_DATE, BuiltInAgentTool.READ_URL).map { toolName ->
            BotToolBinding(
                bindingUid = botBindingUid(uid, null, toolName),
                botUid = uid,
                connectionUid = null,
                toolName = toolName,
                createdAt = 0
            )
        }
        return Definition(bot, bindings)
    }
}
