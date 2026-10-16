package dev.chungjungsoo.gptmobile.presentation.ui.composer

import dev.chungjungsoo.gptmobile.data.prompt.PromptPlaceholders
import dev.chungjungsoo.gptmobile.data.prompt.SavedPrompt

/**
 * Commands the composer can offer. A command is shown only when its feature exists on this build.
 * Skill and clear are omitted because main has neither a skill library nor a clear-conversation action.
 */
enum class ComposerFeature {
    BOT,
    MODEL,
    PROJECT,
    SKILL,
    SEARCH,
    IMAGE,
    TEMPERATURE,
    CLEAR,
    EXPORT
}

enum class SlashCommand(
    val token: String,
    val feature: ComposerFeature
) {
    BOT("bot", ComposerFeature.BOT),
    MODEL("model", ComposerFeature.MODEL),
    PROJECT("project", ComposerFeature.PROJECT),
    SKILL("skill", ComposerFeature.SKILL),
    SEARCH("search", ComposerFeature.SEARCH),
    IMAGE("image", ComposerFeature.IMAGE),
    TEMP("temp", ComposerFeature.TEMPERATURE),
    CLEAR("clear", ComposerFeature.CLEAR),
    EXPORT("export", ComposerFeature.EXPORT)
}

fun composerFeaturesAvailableOnMain(): Set<ComposerFeature> = setOf(
    ComposerFeature.BOT,
    ComposerFeature.MODEL,
    ComposerFeature.PROJECT,
    ComposerFeature.SEARCH,
    ComposerFeature.IMAGE,
    ComposerFeature.TEMPERATURE,
    ComposerFeature.EXPORT
)

fun availableCommands(features: Set<ComposerFeature>): List<SlashCommand> = SlashCommand.entries.filter { it.feature in features }

data class SlashQuery(val filter: String, val start: Int, val end: Int)

data class MentionQuery(val filter: String, val start: Int, val end: Int)

fun detectSlashQuery(text: String, cursor: Int): SlashQuery? {
    if (text.isEmpty() || cursor !in 1..text.length || !text.startsWith("/")) return null
    val lineEnd = text.indexOf('\n').let { index -> if (index == -1) text.length else index }
    if (cursor > lineEnd) return null
    return SlashQuery(filter = text.substring(1, cursor), start = 0, end = cursor)
}

fun detectMentionQuery(text: String, cursor: Int): MentionQuery? {
    if (detectSlashQuery(text, cursor) != null) return null
    if (cursor !in 1..text.length) return null
    val head = text.substring(0, cursor)
    val tokenStart = head.indexOfLast { it.isWhitespace() }.let { index -> if (index == -1) 0 else index + 1 }
    val token = head.substring(tokenStart)
    if (!token.startsWith("@")) return null
    return MentionQuery(filter = token.removePrefix("@"), start = tokenStart, end = cursor)
}

sealed interface SlashMenuRow {
    data class Command(val command: SlashCommand) : SlashMenuRow
    data class Prompt(val prompt: SavedPrompt) : SlashMenuRow
    data object Library : SlashMenuRow
}

fun slashMenuRows(
    filter: String,
    features: Set<ComposerFeature>,
    prompts: List<SavedPrompt>
): List<SlashMenuRow> {
    val word = filter.trim().substringBefore(' ').lowercase()
    val commands = availableCommands(features).filter { command ->
        word.isEmpty() || command.token.startsWith(word)
    }
    val needle = filter.trim().lowercase()
    val promptLimit = if (needle.isEmpty()) 8 else 20
    val matchedPrompts = prompts
        .filter { prompt -> needle.isEmpty() || prompt.title.lowercase().contains(needle) }
        .take(promptLimit)
    return buildList {
        commands.forEach { add(SlashMenuRow.Command(it)) }
        matchedPrompts.forEach { add(SlashMenuRow.Prompt(it)) }
        add(SlashMenuRow.Library)
    }
}

data class MentionCandidate(val key: String, val label: String)

sealed interface MentionMenuRow {
    data class Bot(val candidate: MentionCandidate) : MentionMenuRow
    data class Project(val candidate: MentionCandidate) : MentionMenuRow
}

fun mentionMenuRows(
    filter: String,
    bots: List<MentionCandidate>,
    projects: List<MentionCandidate>
): List<MentionMenuRow> {
    val needle = filter.trim().lowercase()
    fun matches(candidate: MentionCandidate) = needle.isEmpty() || candidate.label.lowercase().contains(needle)
    val limit = if (needle.isEmpty()) 8 else 12
    return buildList {
        bots.filter(::matches).take(limit).forEach { add(MentionMenuRow.Bot(it)) }
        projects.filter(::matches).take(limit).forEach { add(MentionMenuRow.Project(it)) }
    }
}

sealed interface ComposerAction {
    data object None : ComposerAction
    data object OpenBot : ComposerAction
    data object OpenModel : ComposerAction
    data object OpenProject : ComposerAction
    data object PickImage : ComposerAction
    data object ExportChat : ComposerAction
    data object OpenPromptLibrary : ComposerAction
    data object OpenTemperature : ComposerAction
    data class SetTemperature(val value: Float) : ComposerAction
    data object OpenSearch : ComposerAction
    data class SearchConversation(val query: String) : ComposerAction
    data class InsertText(val start: Int, val end: Int, val text: String) : ComposerAction
    data class FillPrompt(val prompt: SavedPrompt, val start: Int, val end: Int) : ComposerAction
    data class MentionBot(val botUid: String, val start: Int, val end: Int, val label: String) : ComposerAction
    data class MentionProject(val projectId: Int, val start: Int, val end: Int, val label: String) : ComposerAction
}

fun parseTemperature(raw: String): Float? {
    val value = raw.trim().toFloatOrNull() ?: return null
    if (value.isNaN() || value < 0f || value > 2f) return null
    return value
}

fun actionForCommand(command: SlashCommand, filter: String): ComposerAction {
    val word = filter.trim().substringBefore(' ')
    val argument = if (word.equals(command.token, ignoreCase = true)) {
        filter.trim().substringAfter(' ', missingDelimiterValue = "").trim()
    } else {
        ""
    }
    return when (command) {
        SlashCommand.BOT -> ComposerAction.OpenBot
        SlashCommand.MODEL -> ComposerAction.OpenModel
        SlashCommand.PROJECT -> ComposerAction.OpenProject
        SlashCommand.SKILL -> ComposerAction.None
        SlashCommand.SEARCH -> if (argument.isEmpty()) ComposerAction.OpenSearch else ComposerAction.SearchConversation(argument)
        SlashCommand.IMAGE -> ComposerAction.PickImage
        SlashCommand.TEMP -> parseTemperature(argument)?.let(ComposerAction::SetTemperature) ?: ComposerAction.OpenTemperature
        SlashCommand.CLEAR -> ComposerAction.None
        SlashCommand.EXPORT -> ComposerAction.ExportChat
    }
}

fun actionForPrompt(prompt: SavedPrompt, start: Int, end: Int): ComposerAction = if (PromptPlaceholders.names(prompt.body).isEmpty()) {
    ComposerAction.InsertText(start, end, prompt.body)
} else {
    ComposerAction.FillPrompt(prompt, start, end)
}

fun mentionInsertion(label: String): String = "@$label "

sealed interface ComposerSubmit {
    data object Message : ComposerSubmit
    data class Run(val action: ComposerAction) : ComposerSubmit
}

fun interpretComposerSubmit(
    text: String,
    features: Set<ComposerFeature>,
    prompts: List<SavedPrompt>
): ComposerSubmit {
    val trimmed = text.trim()
    if (!trimmed.startsWith("/") || trimmed.contains('\n')) return ComposerSubmit.Message
    val filter = trimmed.removePrefix("/").trim()
    val word = filter.substringBefore(' ').lowercase()
    val command = availableCommands(features).firstOrNull { it.token == word }
    if (command != null) {
        val action = actionForCommand(command, filter)
        if (action is ComposerAction.None) return ComposerSubmit.Message
        return ComposerSubmit.Run(action)
    }
    val prompt = prompts.firstOrNull { it.title.equals(filter, ignoreCase = true) } ?: return ComposerSubmit.Message
    return ComposerSubmit.Run(actionForPrompt(prompt, 0, text.length))
}

fun clearsComposer(action: ComposerAction): Boolean = when (action) {
    is ComposerAction.InsertText,
    is ComposerAction.FillPrompt,
    is ComposerAction.MentionBot,
    is ComposerAction.MentionProject -> false
    else -> true
}

fun conversationMatchIndices(
    userContents: List<String>,
    assistantContents: List<List<String>>,
    query: String
): List<Int> {
    val needle = query.trim()
    if (needle.isEmpty()) return userContents.indices.toList()
    return userContents.indices.filter { index ->
        userContents[index].contains(needle, ignoreCase = true) ||
            assistantContents.getOrNull(index).orEmpty().any { it.contains(needle, ignoreCase = true) }
    }
}

fun filledPrompt(body: String, values: Map<String, String>): String = PromptPlaceholders.fill(body, values)
