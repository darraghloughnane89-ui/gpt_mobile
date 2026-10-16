package dev.chungjungsoo.gptmobile.presentation.ui.skill

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.brand.AppBrand
import dev.chungjungsoo.gptmobile.data.skill.Skill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsRoute(
    viewModel: SkillLibraryViewModel,
    onBack: () -> Unit,
    onOpenInbox: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SkillsScreen(
        skills = state.skills,
        message = state.message,
        importOpen = state.importOpen,
        importText = state.importText,
        onBack = onBack,
        onOpenInbox = onOpenInbox,
        onCreate = onCreate,
        onEdit = onEdit,
        onDuplicate = viewModel::duplicate,
        onDelete = viewModel::delete,
        onOpenImport = viewModel::openImport,
        onCloseImport = viewModel::closeImport,
        onImportText = viewModel::updateImportText,
        onImport = viewModel::import,
        onConsumeMessage = viewModel::consumeMessage
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    skills: List<Skill>,
    message: String?,
    importOpen: Boolean,
    importText: String,
    onBack: () -> Unit,
    onOpenInbox: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenImport: () -> Unit,
    onCloseImport: () -> Unit,
    onImportText: (String) -> Unit,
    onImport: () -> Unit,
    onConsumeMessage: () -> Unit
) {
    val scroll = rememberScrollState()
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.skills)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                actions = {
                    TextButton(onClick = onOpenInbox) { Text(stringResource(R.string.skill_inbox)) }
                    TextButton(onClick = onOpenImport) { Text(stringResource(R.string.skill_import)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.skill_create))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.skills_settings_description, AppBrand.NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            message?.let { text ->
                Text(text = text, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onConsumeMessage) { Text(stringResource(R.string.confirm)) }
            }
            if (skills.isEmpty()) {
                Text(
                    text = stringResource(R.string.skill_empty),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            skills.forEach { skill ->
                SkillRow(
                    skill = skill,
                    onOpen = { onEdit(skill.id) },
                    onDuplicate = { onDuplicate(skill.id) },
                    onDelete = { onDelete(skill.id) }
                )
            }
        }
    }
    if (importOpen) {
        AlertDialog(
            onDismissRequest = onCloseImport,
            title = { Text(stringResource(R.string.skill_import)) },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = onImportText,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                    label = { Text(stringResource(R.string.skill_import_hint)) }
                )
            },
            confirmButton = { TextButton(onClick = onImport) { Text(stringResource(R.string.skill_import)) } },
            dismissButton = { TextButton(onClick = onCloseImport) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun SkillRow(
    skill: Skill,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = skill.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.skill_usage_summary, skill.useCount, skill.thumbsUp, skill.thumbsDown),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row {
            TextButton(onClick = onDuplicate) { Text(stringResource(R.string.duplicate)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
        }
    }
}
