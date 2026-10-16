package dev.chungjungsoo.gptmobile.presentation.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.memory.Memory
import dev.chungjungsoo.gptmobile.data.memory.MemorySource
import dev.chungjungsoo.gptmobile.presentation.common.SettingItem
import dev.chungjungsoo.gptmobile.presentation.common.SettingsSection
import dev.chungjungsoo.gptmobile.presentation.ui.privacy.PrivacyScaffold

@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val globallyPaused by viewModel.globallyPaused.collectAsStateWithLifecycle()
    MemoryScreenContent(
        memories = memories,
        globallyPaused = globallyPaused,
        onBack = onBack,
        onGloballyPaused = viewModel::setGloballyPaused,
        onPaused = viewModel::setPaused,
        onUpdateText = viewModel::updateText,
        onAdd = viewModel::addMemory,
        onDelete = viewModel::delete,
        onClearAll = viewModel::clearAll
    )
}

@Composable
fun MemoryScreenContent(
    memories: List<Memory>,
    globallyPaused: Boolean,
    onBack: () -> Unit,
    onGloballyPaused: (Boolean) -> Unit,
    onPaused: (Memory, Boolean) -> Unit,
    onUpdateText: (Long, String) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    var editingId by rememberSaveable { mutableStateOf(-1L) }
    var draft by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val editingMemory = memories.firstOrNull { it.id == editingId }

    PrivacyScaffold(
        title = stringResource(R.string.memory_title),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                draft = ""
                adding = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.memory_add))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.memory_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            SettingsSection(title = stringResource(R.string.memory_controls)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.memory_pause_all), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.memory_pause_all_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = globallyPaused,
                        onCheckedChange = onGloballyPaused
                    )
                }
                SettingItem(
                    title = stringResource(R.string.memory_clear_all),
                    description = stringResource(R.string.memory_clear_all_description),
                    onItemClick = { confirmClear = true },
                    showTrailingIcon = false,
                    showLeadingIcon = false,
                    enabled = memories.isNotEmpty()
                )
            }
            SettingsSection(title = stringResource(R.string.memory_saved)) {
                if (memories.isEmpty()) {
                    Text(
                        text = stringResource(R.string.memory_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else {
                    memories.forEach { memory ->
                        MemoryRow(
                            memory = memory,
                            onEdit = {
                                editingId = memory.id
                                draft = memory.text
                            },
                            onDelete = { onDelete(memory.id) },
                            onPaused = { paused -> onPaused(memory, paused) }
                        )
                    }
                }
            }
        }
    }

    if (adding || editingMemory != null) {
        AlertDialog(
            onDismissRequest = {
                adding = false
                editingId = -1L
            },
            title = {
                Text(
                    stringResource(
                        if (adding) R.string.memory_add else R.string.memory_edit
                    )
                )
            },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.memory_fact)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (adding) {
                            onAdd(draft)
                        } else if (editingMemory != null) {
                            onUpdateText(editingMemory.id, draft)
                        }
                        adding = false
                        editingId = -1L
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    adding = false
                    editingId = -1L
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.memory_clear_all)) },
            text = { Text(stringResource(R.string.memory_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                }) {
                    Text(stringResource(R.string.memory_clear_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun MemoryRow(
    memory: Memory,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPaused: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text = memory.text, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = memorySourceLabel(memory),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.memory_paused),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(checked = memory.paused, onCheckedChange = onPaused)
            IconButton(onClick = onEdit) {
                Text(stringResource(R.string.memory_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.memory_delete))
            }
        }
    }
}

@Composable
private fun memorySourceLabel(memory: Memory): String = when (memory.source) {
    MemorySource.EXPLICIT -> stringResource(R.string.memory_source_explicit)
    MemorySource.SUGGESTION -> stringResource(R.string.memory_source_suggestion)
    else -> memory.source
}

@Composable
fun MemorySuggestionDialog(
    fact: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_suggestion_title)) },
        text = { Text(stringResource(R.string.memory_suggestion_body, fact)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.memory_suggestion_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.memory_suggestion_dismiss))
            }
        }
    )
}
