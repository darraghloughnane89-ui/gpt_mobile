package dev.chungjungsoo.gptmobile.presentation.ui.bot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.bot.BotAvatarPalette
import dev.chungjungsoo.gptmobile.data.bot.BotDraft
import dev.chungjungsoo.gptmobile.data.bot.StarterPrompts
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.ToolConnection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotEditorScreen(
    onNavigationClick: () -> Unit,
    onSaved: () -> Unit,
    viewModel: BotEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val draft = uiState.draft

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        if (viewModel.isEditing) {
                            stringResource(R.string.edit_bot)
                        } else {
                            stringResource(R.string.create_bot)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save(onSaved) },
                        enabled = !uiState.isSaving && !uiState.isLoading
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save_bot))
                    }
                    if (viewModel.isEditing) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.bot_actions))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.duplicate_bot)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.duplicate(onSaved)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_bot)) },
                                onClick = {
                                    menuExpanded = false
                                    confirmDelete = true
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Text(
                text = stringResource(R.string.bots_loading),
                modifier = Modifier.padding(innerPadding).padding(24.dp)
            )
        } else {
            BotEditorForm(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                draft = draft,
                platforms = uiState.platforms,
                searchConnections = uiState.searchConnections,
                mcpConnections = uiState.mcpConnections,
                errorMessage = uiState.errorMessage,
                onDraftChange = viewModel::updateDraft
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_bot_title, draft.name.ifBlank { stringResource(R.string.bot) })) },
            text = { Text(stringResource(R.string.delete_bot_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onSaved)
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun BotEditorForm(
    draft: BotDraft,
    platforms: List<PlatformV2>,
    searchConnections: List<ToolConnection>,
    mcpConnections: List<ToolConnection>,
    errorMessage: String?,
    onDraftChange: ((BotDraft) -> BotDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BotAvatar(emoji = draft.avatarEmoji, colorHex = draft.avatarColor, size = 56.dp)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.name,
                onValueChange = { value -> onDraftChange { it.copy(name = value) } },
                label = { Text(stringResource(R.string.bot_name)) },
                singleLine = true
            )
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft.avatarEmoji,
            onValueChange = { value -> onDraftChange { it.copy(avatarEmoji = value) } },
            label = { Text(stringResource(R.string.bot_avatar_emoji)) },
            supportingText = { Text(stringResource(R.string.bot_avatar_emoji_hint)) },
            singleLine = true
        )
        Text(stringResource(R.string.bot_avatar_color), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotAvatarPalette.colors.forEach { color ->
                BotColorSwatch(
                    colorHex = color,
                    selected = BotAvatarPalette.sanitize(draft.avatarColor) == color,
                    onClick = { onDraftChange { it.copy(avatarColor = color) } }
                )
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft.instructions,
            onValueChange = { value -> onDraftChange { it.copy(instructions = value) } },
            label = { Text(stringResource(R.string.bot_instructions)) },
            supportingText = { Text(stringResource(R.string.bot_instructions_hint)) },
            minLines = 5
        )
        Text(stringResource(R.string.bot_default_platform), style = MaterialTheme.typography.titleSmall)
        PlatformChoice(
            label = stringResource(R.string.bot_no_default_platform),
            selected = draft.defaultPlatformUid == null,
            onClick = { onDraftChange { it.copy(defaultPlatformUid = null) } }
        )
        platforms.filter { it.enabled }.forEach { platform ->
            PlatformChoice(
                label = platform.name,
                selected = draft.defaultPlatformUid == platform.uid,
                onClick = { onDraftChange { it.copy(defaultPlatformUid = platform.uid) } }
            )
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft.defaultModel,
            onValueChange = { value -> onDraftChange { it.copy(defaultModel = value) } },
            label = { Text(stringResource(R.string.bot_default_model)) },
            supportingText = { Text(stringResource(R.string.bot_default_model_hint)) },
            singleLine = true,
            enabled = draft.defaultPlatformUid != null
        )
        HorizontalDivider()
        Text(stringResource(R.string.bot_tools), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.bot_tools_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ToolSwitch(
            title = stringResource(R.string.current_date),
            checked = draft.currentDate,
            onCheckedChange = { checked -> onDraftChange { it.copy(currentDate = checked) } }
        )
        ToolSwitch(
            title = stringResource(R.string.read_url),
            checked = draft.readUrl,
            onCheckedChange = { checked -> onDraftChange { it.copy(readUrl = checked) } }
        )
        if (searchConnections.isNotEmpty()) {
            Text(stringResource(R.string.web_search), style = MaterialTheme.typography.titleSmall)
            PlatformChoice(
                label = stringResource(R.string.bot_tool_off),
                selected = draft.webSearchConnectionUid == null,
                onClick = { onDraftChange { it.copy(webSearchConnectionUid = null) } }
            )
            searchConnections.forEach { connection ->
                PlatformChoice(
                    label = connection.name,
                    selected = draft.webSearchConnectionUid == connection.connectionUid,
                    onClick = { onDraftChange { it.copy(webSearchConnectionUid = connection.connectionUid) } }
                )
            }
        }
        mcpConnections.forEach { connection ->
            val enabled = connection.connectionUid in draft.mcpConnectionUids
            ToolSwitch(
                title = stringResource(R.string.bot_mcp_server, connection.name),
                checked = enabled,
                onCheckedChange = { checked ->
                    onDraftChange { current ->
                        val next = current.mcpConnectionUids.toMutableSet()
                        if (checked) next += connection.connectionUid else next -= connection.connectionUid
                        current.copy(mcpConnectionUids = next)
                    }
                }
            )
        }
        HorizontalDivider()
        Text(stringResource(R.string.starter_prompts), style = MaterialTheme.typography.titleMedium)
        draft.starterPrompts.forEachIndexed { index, prompt ->
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = prompt,
                onValueChange = { value ->
                    onDraftChange { current ->
                        current.copy(
                            starterPrompts = current.starterPrompts.mapIndexed { promptIndex, existing ->
                                if (promptIndex == index) value else existing
                            }
                        )
                    }
                },
                label = { Text(stringResource(R.string.starter_prompt_number, index + 1)) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            onDraftChange { current ->
                                current.copy(starterPrompts = current.starterPrompts.filterIndexed { promptIndex, _ -> promptIndex != index })
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_starter_prompt))
                    }
                }
            )
        }
        if (draft.starterPrompts.size < StarterPrompts.MAX_COUNT) {
            TextButton(
                onClick = { onDraftChange { it.copy(starterPrompts = it.starterPrompts + "") } }
            ) {
                Text(stringResource(R.string.add_starter_prompt))
            }
        }
        errorMessage?.let { message ->
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PlatformChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ToolSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
