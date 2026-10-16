package dev.chungjungsoo.gptmobile.presentation.ui.bot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
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
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.BlurTopBar
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.EmptyState
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.HapticEvent
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.MascotMood
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.Space
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.SurfaceCard
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.fadeSpring
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.playfulSpring
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.rememberHaptic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotListScreen(
    onNavigationClick: () -> Unit,
    onCreateClick: () -> Unit,
    onEditClick: (String) -> Unit,
    viewModel: BotListViewModel = hiltViewModel()
) {
    val bots by viewModel.bots.collectAsStateWithLifecycle()
    val canRestoreBuiltIns by viewModel.canRestoreBuiltIns.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val haptic = rememberHaptic()
    var pendingDelete by remember { mutableStateOf<Bot?>(null) }
    var pendingReset by remember { mutableStateOf<Bot?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BlurTopBar(
                title = stringResource(R.string.bots),
                onNavigation = onNavigationClick,
                navigationDescription = stringResource(R.string.go_back),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic(HapticEvent.Confirm)
                    onCreateClick()
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.create_bot))
            }
        }
    ) { innerPadding ->
        BotListPane(
            bots = bots,
            canRestoreBuiltIns = canRestoreBuiltIns,
            contentPadding = innerPadding,
            onCreateClick = {
                haptic(HapticEvent.Confirm)
                onCreateClick()
            },
            onEditClick = { botUid ->
                haptic(HapticEvent.Tick)
                onEditClick(botUid)
            },
            onDuplicate = { botUid ->
                haptic(HapticEvent.Confirm)
                viewModel.duplicate(botUid)
            },
            onReset = { pendingReset = it },
            onDelete = { pendingDelete = it },
            onRestoreBuiltIns = {
                haptic(HapticEvent.Confirm)
                viewModel.restoreDeletedBuiltIns()
            }
        )
    }

    pendingDelete?.let { bot ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_bot_title, bot.name)) },
            text = { Text(stringResource(R.string.delete_bot_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic(HapticEvent.Reject)
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
                        haptic(HapticEvent.Confirm)
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
fun BotListPane(
    bots: List<Bot>,
    canRestoreBuiltIns: Boolean,
    onCreateClick: () -> Unit,
    onEditClick: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onReset: (Bot) -> Unit,
    onDelete: (Bot) -> Unit,
    onRestoreBuiltIns: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    animated: Boolean = true
) {
    if (bots.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    title = stringResource(R.string.shell_bots_title),
                    body = stringResource(R.string.shell_bots_body),
                    mood = MascotMood.Wave,
                    animated = animated,
                    actionLabel = stringResource(R.string.create_bot),
                    onAction = onCreateClick
                )
                if (canRestoreBuiltIns) {
                    TextButton(onClick = onRestoreBuiltIns) {
                        Text(stringResource(R.string.restore_built_in_bots))
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.lg,
                end = Space.lg,
                top = contentPadding.calculateTopPadding() + Space.sm,
                bottom = contentPadding.calculateBottomPadding() + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            if (canRestoreBuiltIns) {
                item(key = "restore-built-ins") {
                    TextButton(onClick = onRestoreBuiltIns) {
                        Text(stringResource(R.string.restore_built_in_bots))
                    }
                }
            }
            items(bots, key = { it.botUid }) { bot ->
                BotListRow(
                    bot = bot,
                    animated = animated,
                    onClick = { onEditClick(bot.botUid) },
                    onDuplicate = { onDuplicate(bot.botUid) },
                    onReset = if (bot.builtin) {
                        { onReset(bot) }
                    } else {
                        null
                    },
                    onDelete = { onDelete(bot) }
                )
            }
        }
    }
}

@Composable
private fun BotListRow(
    bot: Bot,
    animated: Boolean,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onReset: (() -> Unit)?,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val lift by animateDpAsState(
        targetValue = if (menuExpanded) 6.dp else 0.dp,
        animationSpec = playfulSpring(),
        label = "bot-row"
    )
    val row: @Composable () -> Unit = {
        SurfaceCard(
            onClick = onClick,
            modifier = Modifier.padding(top = if (animated) lift else 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Space.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                BotAvatar(emoji = bot.avatarEmoji, colorHex = bot.avatarColor)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.xxs)
                ) {
                    Text(
                        text = bot.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (bot.builtin) {
                        Text(
                            text = stringResource(R.string.built_in_bot),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = bot.instructions.ifBlank { stringResource(R.string.bot_no_instructions) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
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
        }
    }
    if (animated) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(fadeSpring()),
            exit = fadeOut(fadeSpring())
        ) {
            row()
        }
    } else {
        row()
    }
}
