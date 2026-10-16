package dev.chungjungsoo.gptmobile.presentation.ui.bot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.presentation.common.ContextualTip
import dev.chungjungsoo.gptmobile.presentation.common.NestEmptyState
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.MascotMood
import dev.chungjungsoo.gptmobile.presentation.ui.library.LibraryTipViewModel
import dev.chungjungsoo.gptmobile.presentation.ui.library.TipIds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotListScreen(
    onNavigationClick: () -> Unit,
    onCreateClick: () -> Unit,
    onEditClick: (String) -> Unit,
    viewModel: BotListViewModel = hiltViewModel(),
    tipViewModel: LibraryTipViewModel = hiltViewModel()
) {
    val bots by viewModel.bots.collectAsStateWithLifecycle()
    val canRestoreBuiltIns by viewModel.canRestoreBuiltIns.collectAsStateWithLifecycle()
    val dismissedTips by tipViewModel.dismissed.collectAsStateWithLifecycle()
    val showBotTip = TipIds.BOTS !in dismissedTips
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var pendingDelete by remember { mutableStateOf<Bot?>(null) }
    var pendingReset by remember { mutableStateOf<Bot?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.bots)) },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.create_bot))
            }
        }
    ) { innerPadding ->
        if (bots.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NestEmptyState(
                    title = stringResource(R.string.shell_bots_title),
                    body = stringResource(R.string.shell_bots_body),
                    mood = MascotMood.Wave,
                    modifier = Modifier.weight(1f),
                    actionLabel = stringResource(R.string.create_bot),
                    onAction = onCreateClick,
                    tip = stringResource(R.string.tip_bots).takeIf { showBotTip },
                    onDismissTip = { tipViewModel.dismiss(TipIds.BOTS) }
                )
                if (canRestoreBuiltIns) {
                    TextButton(
                        onClick = viewModel::restoreDeletedBuiltIns,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.restore_built_in_bots))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 88.dp
                )
            ) {
                if (showBotTip) {
                    item(key = "bot-tip") {
                        ContextualTip(
                            text = stringResource(R.string.tip_bots),
                            onDismiss = { tipViewModel.dismiss(TipIds.BOTS) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                if (canRestoreBuiltIns) {
                    item(key = "restore-built-ins") {
                        TextButton(
                            onClick = viewModel::restoreDeletedBuiltIns,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(stringResource(R.string.restore_built_in_bots))
                        }
                    }
                }
                items(bots, key = { it.botUid }) { bot ->
                    BotListRow(
                        bot = bot,
                        onClick = { onEditClick(bot.botUid) },
                        onDuplicate = { viewModel.duplicate(bot.botUid) },
                        onReset = if (bot.builtin) {
                            { pendingReset = bot }
                        } else {
                            null
                        },
                        onDelete = { pendingDelete = bot }
                    )
                }
            }
        }
    }

    pendingDelete?.let { bot ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_bot_title, bot.name)) },
            text = { Text(stringResource(R.string.delete_bot_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(bot.botUid)
                        pendingDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingReset?.let { bot ->
        AlertDialog(
            onDismissRequest = { pendingReset = null },
            title = { Text(stringResource(R.string.reset_bot_title, bot.name)) },
            text = { Text(stringResource(R.string.reset_bot_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.reset(bot.botUid)
                        pendingReset = null
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingReset = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun BotListRow(
    bot: Bot,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onReset: (() -> Unit)?,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            BotAvatar(emoji = bot.avatarEmoji, colorHex = bot.avatarColor)
        },
        headlineContent = {
            Text(bot.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (bot.builtin) {
                    Text(
                        text = stringResource(R.string.built_in_bot),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = bot.instructions.ifBlank { stringResource(R.string.bot_no_instructions) },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.bot_actions))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_bot)) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate_bot)) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    if (onReset != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reset)) },
                            onClick = {
                                menuExpanded = false
                                onReset()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_bot)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    )
}
