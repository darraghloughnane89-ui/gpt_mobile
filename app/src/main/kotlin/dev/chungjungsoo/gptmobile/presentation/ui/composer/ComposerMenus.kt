package dev.chungjungsoo.gptmobile.presentation.ui.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.data.database.entity.Project
import dev.chungjungsoo.gptmobile.data.prompt.PromptPlaceholders
import dev.chungjungsoo.gptmobile.data.prompt.SavedPrompt

@Composable
fun ComposerCommandBar(
    inputState: TextFieldState,
    prompts: List<SavedPrompt>,
    bots: List<Bot>,
    projects: List<Project>,
    features: Set<ComposerFeature>,
    onAction: (ComposerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var slash by remember { mutableStateOf<SlashQuery?>(null) }
    var mention by remember { mutableStateOf<MentionQuery?>(null) }
    LaunchedEffect(inputState) {
        snapshotFlow {
            val text = inputState.text.toString()
            val cursor = maxOf(inputState.selection.start, inputState.selection.end)
            text to cursor.coerceIn(0, text.length)
        }.collect { (text, cursor) ->
            slash = detectSlashQuery(text, cursor)
            mention = detectMentionQuery(text, cursor)
        }
    }

    val slashQuery = slash
    val mentionQuery = mention
    if (slashQuery != null) {
        SlashMenu(
            filter = slashQuery.filter,
            prompts = prompts,
            features = features,
            onAction = onAction,
            modifier = modifier
        )
    } else if (mentionQuery != null) {
        MentionMenu(
            query = mentionQuery,
            bots = bots,
            projects = projects,
            onAction = onAction,
            modifier = modifier
        )
    }
}

@Composable
private fun SlashMenu(
    filter: String,
    prompts: List<SavedPrompt>,
    features: Set<ComposerFeature>,
    onAction: (ComposerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = slashMenuRows(filter, features, prompts)
    SuggestionSurface(modifier) {
        var commandsStarted = false
        var promptsStarted = false
        for (row in rows) {
            when (row) {
                is SlashMenuRow.Command -> {
                    if (!commandsStarted) {
                        SectionLabel(stringResource(R.string.slash_commands))
                        commandsStarted = true
                    }
                    ListItem(
                        modifier = Modifier.clickable { onAction(actionForCommand(row.command, filter)) },
                        headlineContent = { Text("/${row.command.token}") },
                        supportingContent = { Text(SlashCommandDescription(row.command)) }
                    )
                }

                is SlashMenuRow.Prompt -> {
                    if (!promptsStarted) {
                        if (commandsStarted) HorizontalDivider()
                        SectionLabel(stringResource(R.string.slash_saved_prompts))
                        promptsStarted = true
                    }
                    ListItem(
                        modifier = Modifier.clickable { onAction(actionForPrompt(row.prompt, 0, filter.length + 1)) },
                        headlineContent = { Text(row.prompt.title) },
                        supportingContent = {
                            Text(
                                text = row.prompt.body,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingContent = if (row.prompt.favourite) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = stringResource(R.string.prompt_favourite)
                                )
                            }
                        } else {
                            null
                        }
                    )
                }

                SlashMenuRow.Library -> {
                    HorizontalDivider()
                    ListItem(
                        modifier = Modifier.clickable { onAction(ComposerAction.OpenPromptLibrary) },
                        headlineContent = { Text(stringResource(R.string.slash_manage_prompts)) },
                        supportingContent = { Text(stringResource(R.string.prompt_library_description)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MentionMenu(
    query: MentionQuery,
    bots: List<Bot>,
    projects: List<Project>,
    onAction: (ComposerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = mentionMenuRows(
        filter = query.filter,
        bots = bots.map { MentionCandidate(it.botUid, it.name) },
        projects = projects.map { MentionCandidate(it.id.toString(), it.name) }
    )
    if (rows.isEmpty()) return
    SuggestionSurface(modifier) {
        var botsStarted = false
        var projectsStarted = false
        for (row in rows) {
            when (row) {
                is MentionMenuRow.Bot -> {
                    if (!botsStarted) {
                        SectionLabel(stringResource(R.string.mention_bots))
                        botsStarted = true
                    }
                    ListItem(
                        modifier = Modifier.clickable {
                            onAction(
                                ComposerAction.MentionBot(
                                    botUid = row.candidate.key,
                                    start = query.start,
                                    end = query.end,
                                    label = row.candidate.label
                                )
                            )
                        },
                        headlineContent = { Text("@${row.candidate.label}") },
                        supportingContent = { Text(stringResource(R.string.bots)) }
                    )
                }

                is MentionMenuRow.Project -> {
                    if (!projectsStarted) {
                        if (botsStarted) HorizontalDivider()
                        SectionLabel(stringResource(R.string.mention_projects))
                        projectsStarted = true
                    }
                    val projectId = row.candidate.key.toIntOrNull() ?: continue
                    ListItem(
                        modifier = Modifier.clickable {
                            onAction(
                                ComposerAction.MentionProject(
                                    projectId = projectId,
                                    start = query.start,
                                    end = query.end,
                                    label = row.candidate.label
                                )
                            )
                        },
                        headlineContent = { Text("@${row.candidate.label}") },
                        supportingContent = { Text(stringResource(R.string.projects)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionSurface(
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
        ) {
            content()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SlashCommandDescription(command: SlashCommand): String = when (command) {
    SlashCommand.BOT -> stringResource(R.string.slash_bot_description)
    SlashCommand.MODEL -> stringResource(R.string.slash_model_description)
    SlashCommand.PROJECT -> stringResource(R.string.slash_project_description)
    SlashCommand.SKILL -> stringResource(R.string.slash_skill_description)
    SlashCommand.SEARCH -> stringResource(R.string.slash_search_description)
    SlashCommand.IMAGE -> stringResource(R.string.slash_image_description)
    SlashCommand.TEMP -> stringResource(R.string.slash_temp_description)
    SlashCommand.CLEAR -> stringResource(R.string.slash_clear_description)
    SlashCommand.EXPORT -> stringResource(R.string.slash_export_description)
}

@Composable
fun PromptPlaceholderDialog(
    prompt: SavedPrompt,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val names = remember(prompt.body) { PromptPlaceholders.names(prompt.body) }
    var values by remember(prompt.id) { mutableStateOf(names.associateWith { "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prompt_placeholders_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = prompt.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                names.forEach { name ->
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        value = values[name].orEmpty(),
                        onValueChange = { value -> values = values + (name to value) },
                        label = { Text(stringResource(R.string.prompt_placeholder_hint, name)) },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = names.all { values[it].orEmpty().isNotBlank() },
                onClick = { onConfirm(filledPrompt(prompt.body, values)) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun ConversationSearchDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_conversation)) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_chats)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = query.isNotBlank(),
                onClick = { onConfirm(query.trim()) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun ChatTemperatureDialog(
    initial: Float?,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var text by remember { mutableStateOf(initial?.let { "%.1f".format(it) } ?: "1.0") }
    val parsed = parseTemperature(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.temperature_setting)) },
        text = {
            Column {
                Text(stringResource(R.string.slash_temp_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.temperature)) },
                    supportingText = if (parsed == null && text.isNotBlank()) {
                        { Text(stringResource(R.string.temperature_invalid)) }
                    } else {
                        null
                    },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = { parsed?.let(onConfirm) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun ConversationSearchBanner(
    query: String,
    matchCount: Int,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier.padding(horizontal = 8.dp),
        headlineContent = {
            Text(
                text = if (matchCount == 0) {
                    stringResource(R.string.conversation_search_empty, query)
                } else {
                    stringResource(R.string.conversation_search_matches, query)
                }
            )
        },
        trailingContent = {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.clear_search))
            }
        }
    )
}

internal fun TextFieldState.replaceComposerRange(start: Int, end: Int, insertion: String) {
    val safeStart = start.coerceIn(0, text.length)
    val safeEnd = end.coerceIn(safeStart, text.length)
    edit {
        replace(safeStart, safeEnd, insertion)
        selection = TextRange((safeStart + insertion.length).coerceAtMost(length))
    }
}
