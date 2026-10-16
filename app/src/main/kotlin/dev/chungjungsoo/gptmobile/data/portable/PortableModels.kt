package dev.chungjungsoo.gptmobile.data.portable

import dev.chungjungsoo.gptmobile.data.bot.BotDraft
import dev.chungjungsoo.gptmobile.data.database.entity.Bot as BotRecord
import dev.chungjungsoo.gptmobile.data.database.entity.BotToolBinding

enum class PortableFailure {
    TOO_LARGE,
    LINK_TOO_LARGE,
    NOT_A_DOCUMENT,
    UNSUPPORTED_VERSION,
    KIND_MISMATCH,
    INVALID_ITEM,
    LIBRARY_FULL
}

enum class PortableWarning {
    STRIPPED_SECRETS,
    IGNORED_SIGNATURE,
    IGNORED_IDENTITY,
    SLUG_ADJUSTED
}

enum class PortableToolDecision {
    /** Built-in bot tool with no credential. Turned on after the person confirms. */
    ENABLE,

    /** Skill requirement text. Stored on the skill. No connection is created. */
    RECORD_ONLY,

    /** Needs a key, a server, or is unknown. Left off. */
    WITHHOLD
}

data class PortableToolRequest(
    val name: String,
    val decision: PortableToolDecision
)

data class PortableBotBody(
    val name: String,
    val avatarEmoji: String?,
    val avatarColor: String,
    val instructions: String,
    val defaultModel: String?,
    val starterPrompts: List<String>,
    val tools: List<String>
)

data class PortableSkillBody(
    val name: String,
    val title: String,
    val description: String,
    val instructions: String,
    val tools: List<String>,
    val examples: List<String>
)

data class PortablePromptBody(
    val title: String,
    val text: String
)

sealed class PortablePreview {
    abstract val title: String
    abstract val instructions: String
    abstract val description: String
    abstract val tools: List<PortableToolRequest>
    abstract val lines: List<String>
    abstract val modelHint: String?
    abstract val warnings: Set<PortableWarning>

    val kind: PortableKind
        get() = when (this) {
            is Bot -> PortableKind.BOT
            is Skill -> PortableKind.SKILL
            is Prompt -> PortableKind.PROMPT
        }

    data class Bot(
        val body: PortableBotBody,
        override val tools: List<PortableToolRequest>,
        override val warnings: Set<PortableWarning>
    ) : PortablePreview() {
        override val title: String = body.name
        override val instructions: String = body.instructions
        override val description: String = ""
        override val lines: List<String> = body.starterPrompts
        override val modelHint: String? = body.defaultModel

        fun toEntities(uid: String, now: Long): Pair<BotRecord, List<BotToolBinding>> {
            val grants = PortablePolicy.botGrants(body.tools)
            val draft = BotDraft(
                name = body.name,
                avatarEmoji = body.avatarEmoji.orEmpty(),
                avatarColor = body.avatarColor,
                instructions = body.instructions,
                defaultPlatformUid = null,
                defaultModel = body.defaultModel.orEmpty(),
                starterPrompts = body.starterPrompts,
                builtin = false,
                currentDate = grants.currentDate,
                readUrl = grants.readUrl,
                webSearchConnectionUid = null,
                mcpConnectionUids = emptySet()
            )
            return draft.toBot(uid = uid, now = now, createdAt = now) to draft.toBindings(uid, now)
        }
    }

    data class Skill(
        val body: PortableSkillBody,
        override val tools: List<PortableToolRequest>,
        override val warnings: Set<PortableWarning>
    ) : PortablePreview() {
        override val title: String = body.title
        override val instructions: String = body.instructions
        override val description: String = body.description
        override val lines: List<String> = body.examples
        override val modelHint: String? = null

        fun toStored(id: String, now: Long): StoredSkill = StoredSkill(
            id = id,
            name = body.name,
            title = body.title,
            description = body.description,
            instructions = body.instructions,
            tools = body.tools,
            examples = body.examples,
            untrusted = true,
            createdAt = now
        )
    }

    data class Prompt(
        val body: PortablePromptBody,
        override val tools: List<PortableToolRequest>,
        override val warnings: Set<PortableWarning>
    ) : PortablePreview() {
        override val title: String = body.title
        override val instructions: String = body.text
        override val description: String = ""
        override val lines: List<String> = emptyList()
        override val modelHint: String? = null

        fun toStored(id: String, now: Long): StoredPrompt = StoredPrompt(
            id = id,
            title = body.title,
            text = body.text,
            untrusted = true,
            createdAt = now
        )
    }
}

sealed class PortableResult {
    data class Ok(val preview: PortablePreview) : PortableResult()
    data class Failure(val reason: PortableFailure) : PortableResult()
}
