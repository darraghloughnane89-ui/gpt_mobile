package dev.chungjungsoo.gptmobile.data.bot

import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.data.database.entity.BotToolBinding
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import java.nio.charset.StandardCharsets
import java.util.UUID

data class BotDraft(
    val botUid: String? = null,
    val name: String = "",
    val avatarEmoji: String = "",
    val avatarColor: String = BotAvatarPalette.DEFAULT,
    val instructions: String = "",
    val defaultPlatformUid: String? = null,
    val defaultModel: String = "",
    val starterPrompts: List<String> = emptyList(),
    val builtin: Boolean = false,
    val currentDate: Boolean = false,
    val readUrl: Boolean = false,
    val runJavascript: Boolean = false,
    val webSearchConnectionUid: String? = null,
    val mcpConnectionUids: Set<String> = emptySet()
) {
    fun sanitized(): BotDraft = copy(
        name = name.trim().take(MAX_NAME_LENGTH),
        avatarEmoji = avatarEmoji.trim().take(MAX_EMOJI_LENGTH),
        avatarColor = BotAvatarPalette.sanitize(avatarColor),
        instructions = instructions.trim().take(MAX_INSTRUCTIONS_LENGTH),
        defaultPlatformUid = defaultPlatformUid?.takeIf { it.isNotBlank() },
        defaultModel = defaultModel.trim().take(MAX_MODEL_LENGTH),
        starterPrompts = StarterPrompts.decode(StarterPrompts.encode(starterPrompts))
    )

    fun toBot(uid: String, now: Long, createdAt: Long = now): Bot {
        val clean = sanitized()
        return Bot(
            botUid = uid,
            name = clean.name,
            avatarEmoji = clean.avatarEmoji.takeIf { it.isNotEmpty() },
            avatarColor = clean.avatarColor,
            instructions = clean.instructions,
            defaultPlatformUid = clean.defaultPlatformUid,
            defaultModel = clean.defaultModel.takeIf { it.isNotEmpty() },
            starterPrompts = StarterPrompts.encode(clean.starterPrompts),
            builtin = builtin,
            createdAt = createdAt,
            updatedAt = now
        )
    }

    fun toBindings(botUid: String, now: Long): List<BotToolBinding> {
        val clean = sanitized()
        val bindings = mutableListOf<BotToolBinding>()
        if (clean.currentDate) {
            bindings += binding(botUid, null, BuiltInAgentTool.CURRENT_DATE, now)
        }
        if (clean.readUrl) {
            bindings += binding(botUid, null, BuiltInAgentTool.READ_URL, now)
        }
        if (clean.runJavascript) {
            bindings += binding(botUid, null, BuiltInAgentTool.RUN_JAVASCRIPT, now)
        }
        clean.webSearchConnectionUid?.let { connectionUid ->
            bindings += binding(botUid, connectionUid, BotToolNames.WEB_SEARCH, now)
        }
        clean.mcpConnectionUids.sorted().forEach { connectionUid ->
            bindings += binding(botUid, connectionUid, BotToolNames.MCP_ALL, now)
        }
        return bindings
    }

    companion object {
        const val MAX_NAME_LENGTH = 80
        const val MAX_EMOJI_LENGTH = 8
        const val MAX_INSTRUCTIONS_LENGTH = 8_000
        const val MAX_MODEL_LENGTH = 200

        fun fromBot(bot: Bot, bindings: List<BotToolBinding>): BotDraft = BotDraft(
            botUid = bot.botUid,
            name = bot.name,
            avatarEmoji = bot.avatarEmoji.orEmpty(),
            avatarColor = BotAvatarPalette.sanitize(bot.avatarColor),
            instructions = bot.instructions,
            defaultPlatformUid = bot.defaultPlatformUid,
            defaultModel = bot.defaultModel.orEmpty(),
            starterPrompts = bot.starterPromptList(),
            builtin = bot.builtin,
            currentDate = bindings.any { it.toolName == BuiltInAgentTool.CURRENT_DATE && it.connectionUid == null },
            readUrl = bindings.any { it.toolName == BuiltInAgentTool.READ_URL && it.connectionUid == null },
            runJavascript = bindings.any { it.toolName == BuiltInAgentTool.RUN_JAVASCRIPT && it.connectionUid == null },
            webSearchConnectionUid = bindings.firstOrNull { it.toolName == BotToolNames.WEB_SEARCH }?.connectionUid,
            mcpConnectionUids = bindings
                .filter { it.toolName == BotToolNames.MCP_ALL }
                .mapNotNull { it.connectionUid }
                .toSet()
        )
    }
}

fun duplicatedBot(source: Bot, newUid: String, now: Long): Bot {
    val baseName = source.name.trim().ifEmpty { "Bot" }.take(BotDraft.MAX_NAME_LENGTH)
    val copyName = "$baseName copy".take(BotDraft.MAX_NAME_LENGTH)
    return source.copy(
        botUid = newUid,
        name = copyName,
        builtin = false,
        createdAt = now,
        updatedAt = now
    )
}

fun botBindingUid(botUid: String, connectionUid: String?, toolName: String): String {
    val key = "bot|$botUid|${connectionUid ?: "builtin"}|$toolName"
    return UUID.nameUUIDFromBytes(key.toByteArray(StandardCharsets.UTF_8)).toString()
}

private fun binding(
    botUid: String,
    connectionUid: String?,
    toolName: String,
    now: Long
) = BotToolBinding(
    bindingUid = botBindingUid(botUid, connectionUid, toolName),
    botUid = botUid,
    connectionUid = connectionUid,
    toolName = toolName,
    createdAt = now
)
