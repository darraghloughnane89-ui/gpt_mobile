package dev.chungjungsoo.gptmobile.presentation.ui.prompt

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.prompt.SavedPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLibraryScreen(
    onBack: () -> Unit,
    viewModel: PromptLibraryViewModel = hiltViewModel()
) {
    val prompts by viewModel.prompts.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<SavedPrompt?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = readText(context, uri)
            if (raw == null) {
                Toast.makeText(context, R.string.prompts_import_failed, Toast.LENGTH_SHORT).show()
            } else {
                viewModel.importJson(raw)
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = viewModel.exportJson()
            val wrote = writeText(context, uri, json)
            if (!wrote) {
                Toast.makeText(context, R.string.prompts_import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(status) {
        when (val current = status) {
            null -> return@LaunchedEffect
            PromptLibraryStatus.NeedsText -> Toast.makeText(context, R.string.prompt_needs_title_and_body, Toast.LENGTH_SHORT).show()
            PromptLibraryStatus.Saved -> Toast.makeText(context, R.string.prompt_saved, Toast.LENGTH_SHORT).show()
            PromptLibraryStatus.Unreadable -> Toast.makeText(context, R.string.prompts_import_failed, Toast.LENGTH_SHORT).show()
            is PromptLibraryStatus.Imported -> Toast.makeText(
                context,
                context.getString(
                    R.string.prompts_imported,
                    current.counts.added,
                    current.counts.updated,
                    current.counts.skipped
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
        viewModel.consumeStatus()
    }

    val currentDraft = draft
    if (currentDraft != null) {
        PromptEditor(
            draft = currentDraft,
            onBack = viewModel::dismissDraft,
            onTitle = { viewModel.updateDraft(title = it) },
            onBody = { viewModel.updateDraft(body = it) },
            onFavourite = { viewModel.updateDraft(favourite = it) },
            onSave = viewModel::saveDraft,
            onDelete = if (currentDraft.isNew || currentDraft.id == null) {
                null
            } else {
                {
                    pendingDelete = SavedPrompt(
                        id = currentDraft.id,
                        title = currentDraft.title,
                        body = currentDraft.body,
                        favourite = currentDraft.favourite
                    )
                }
            }
        )
    } else {
        PromptList(
            prompts = prompts,
            onBack = onBack,
            onCreate = viewModel::startCreate,
            onEdit = viewModel::startEdit,
            onFavourite = viewModel::setFavourite,
            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "text/*")) },
            onExport = { exportLauncher.launch("prompts.json") }
        )
    }

    pendingDelete?.let { prompt ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_prompt_title)) },
            text = { Text(stringResource(R.string.delete_prompt_message, prompt.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(prompt.id)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptList(
    prompts: List<SavedPrompt>,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onFavourite: (String, Boolean) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.prompt_library)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                actions = {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(R.string.import_prompts))
                    }
                    IconButton(onClick = onExport, enabled = prompts.isNotEmpty()) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = stringResource(R.string.export_prompts))
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
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.create_prompt))
            }
        }
    ) { innerPadding ->
        if (prompts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.no_prompts), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.no_prompts_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 88.dp
                )
            ) {
                items(prompts, key = { it.id }) { prompt ->
                    ListItem(
                        headlineContent = { Text(prompt.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(prompt.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            IconButton(onClick = { onFavourite(prompt.id, !prompt.favourite) }) {
                                Icon(
                                    imageVector = if (prompt.favourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = stringResource(
                                        if (prompt.favourite) R.string.unmark_favourite else R.string.mark_favourite
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .clickable { onEdit(prompt.id) }
                            .padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptEditor(
    draft: PromptDraft,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onBody: (String) -> Unit,
    onFavourite: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(stringResource(if (draft.isNew) R.string.create_prompt else R.string.edit_prompt))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.title,
                onValueChange = onTitle,
                label = { Text(stringResource(R.string.prompt_title)) },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.body,
                onValueChange = onBody,
                label = { Text(stringResource(R.string.prompt_body)) },
                supportingText = { Text(stringResource(R.string.prompt_body_hint)) },
                minLines = 6
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.prompt_favourite))
                Switch(
                    checked = draft.favourite,
                    onCheckedChange = onFavourite
                )
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Text(
                        text = stringResource(R.string.delete),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

private suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()
}

private suspend fun writeText(context: Context, uri: Uri, contents: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(contents.toByteArray(Charsets.UTF_8))
        } != null
    }.getOrDefault(false)
}
