package dev.chungjungsoo.gptmobile.presentation.ui.skill

import android.content.ClipData
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillEditorRoute(
    viewModel: SkillEditorViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    SkillEditorScreen(
        state = state,
        onBack = onBack,
        onName = { value -> viewModel.update { it.copy(name = value, message = null) } },
        onTitle = { value -> viewModel.update { it.copy(title = value) } },
        onDescription = { value -> viewModel.update { it.copy(description = value, message = null) } },
        onInstructions = { value -> viewModel.update { it.copy(instructions = value, message = null) } },
        onTools = { value -> viewModel.update { it.copy(tools = value) } },
        onExamples = { value -> viewModel.update { it.copy(examples = value) } },
        onPinned = { value -> viewModel.update { it.copy(pinned = value) } },
        onSave = { viewModel.save(onBack) },
        onExport = viewModel::export
    )
    state.exportText?.let { markdown ->
        AlertDialog(
            onDismissRequest = viewModel::consumeExport,
            title = { Text(stringResource(R.string.skill_export)) },
            text = {
                Text(text = markdown, style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("SKILL.md", markdown)))
                        }
                        viewModel.consumeExport()
                    }
                ) { Text(stringResource(R.string.skill_copy)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::consumeExport) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillEditorScreen(
    state: SkillEditorState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onTitle: (String) -> Unit,
    onDescription: (String) -> Unit,
    onInstructions: (String) -> Unit,
    onTools: (String) -> Unit,
    onExamples: (String) -> Unit,
    onPinned: (Boolean) -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit
) {
    val scroll = rememberScrollState()
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.skill_editor)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                actions = {
                    TextButton(onClick = onExport) { Text(stringResource(R.string.skill_export)) }
                    TextButton(onClick = onSave) { Text(stringResource(R.string.save)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.message?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(
                value = state.name,
                onValueChange = onName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.skill_name)) },
                singleLine = true
            )
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitle,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.skill_title)) },
                singleLine = true
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescription,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.skill_when_to_use)) },
                minLines = 2
            )
            OutlinedTextField(
                value = state.instructions,
                onValueChange = onInstructions,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.skill_procedure)) },
                minLines = 6
            )
            OutlinedTextField(
                value = state.tools,
                onValueChange = onTools,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.skill_tools)) },
                singleLine = true
            )
            OutlinedTextField(
                value = state.examples,
                onValueChange = onExamples,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.skill_examples)) },
                minLines = 3
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.skill_pinned))
                Switch(checked = state.pinned, onCheckedChange = onPinned)
            }
        }
    }
}
