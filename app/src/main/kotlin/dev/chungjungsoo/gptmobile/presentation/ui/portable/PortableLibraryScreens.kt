package dev.chungjungsoo.gptmobile.presentation.ui.portable

import android.content.ClipData
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
import androidx.compose.material.icons.filled.FileOpen
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.portable.PortableKind
import kotlinx.coroutines.launch

private val IMPORT_MIME_TYPES = arrayOf(
    PortableKind.BOT.mime,
    PortableKind.SKILL.mime,
    PortableKind.PROMPT.mime,
    "application/json",
    "application/octet-stream"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortableSkillListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onImportPreview: () -> Unit,
    viewModel: PortableSkillLibraryViewModel = hiltViewModel(),
    bridge: PortableImportBridgeViewModel = hiltViewModel()
) {
    val skills = viewModel.state.collectAsStateWithLifecycle().value.skills
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    ItemLibrary(
        title = stringResource(R.string.portable_skills),
        empty = stringResource(R.string.portable_empty_skills),
        isEmpty = skills.isEmpty(),
        onBack = onBack,
        onCreate = onCreate,
        onImportPreview = onImportPreview,
        bridge = bridge
    ) {
        items(skills, key = { it.id }) { skill ->
            ShareRow(
                title = skill.title,
                body = skill.description,
                untrusted = skill.untrusted,
                onClick = { onEdit(skill.id) },
                onDelete = { viewModel.delete(skill.id) },
                exportJson = { viewModel.exportJson(skill.id) },
                kind = PortableKind.SKILL
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPromptListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onImportPreview: () -> Unit,
    viewModel: SavedPromptLibraryViewModel = hiltViewModel(),
    bridge: PortableImportBridgeViewModel = hiltViewModel()
) {
    val prompts = viewModel.state.collectAsStateWithLifecycle().value.prompts
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    ItemLibrary(
        title = stringResource(R.string.saved_prompts),
        empty = stringResource(R.string.portable_empty_prompts),
        isEmpty = prompts.isEmpty(),
        onBack = onBack,
        onCreate = onCreate,
        onImportPreview = onImportPreview,
        bridge = bridge
    ) {
        items(prompts, key = { it.id }) { prompt ->
            ShareRow(
                title = prompt.title,
                body = prompt.text,
                untrusted = prompt.untrusted,
                onClick = { onEdit(prompt.id) },
                onDelete = { viewModel.delete(prompt.id) },
                exportJson = { viewModel.exportJson(prompt.id) },
                kind = PortableKind.PROMPT
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemLibrary(
    title: String,
    empty: String,
    isEmpty: Boolean,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onImportPreview: () -> Unit,
    bridge: PortableImportBridgeViewModel,
    rows: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        bridge.inbox.offer(bridge.intake.read(context.contentResolver, uri))
        onImportPreview()
    }
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                actions = {
                    IconButton(onClick = { launcher.launch(IMPORT_MIME_TYPES) }) {
                        Icon(Icons.Filled.FileOpen, contentDescription = stringResource(R.string.portable_import_action))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.create))
            }
        }
    ) { innerPadding ->
        if (isEmpty) {
            Text(
                text = empty,
                modifier = Modifier.padding(innerPadding).padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 88.dp
                )
            ) {
                rows()
            }
        }
    }
}

@Composable
private fun ShareRow(
    title: String,
    body: String,
    untrusted: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    exportJson: suspend () -> String?,
    kind: PortableKind
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val shareTitle = stringResource(R.string.share_portable_chooser)
    val copied = stringResource(R.string.portable_link_copied)
    val linkTooBig = stringResource(R.string.portable_link_too_large)
    val shareFailed = stringResource(R.string.portable_share_failed)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (untrusted) {
                    Text(
                        text = stringResource(R.string.portable_imported_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.bot_actions))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share_portable)) },
                        onClick = {
                            menu = false
                            scope.launch {
                                val json = exportJson()
                                if (json == null) {
                                    Toast.makeText(context, shareFailed, Toast.LENGTH_SHORT).show()
                                } else {
                                    runCatching {
                                        sharePortableDocument(context, json, kind, title, shareTitle)
                                    }.onFailure {
                                        Toast.makeText(context, shareFailed, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.portable_copy_link)) },
                        onClick = {
                            menu = false
                            scope.launch {
                                val json = exportJson()
                                val link = json?.let(::portableLink)
                                if (link == null) {
                                    Toast.makeText(context, linkTooBig, Toast.LENGTH_SHORT).show()
                                } else {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("robin", link)))
                                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = {
                            menu = false
                            confirmDelete = true
                        }
                    )
                }
            }
        }
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.portable_delete_title, title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortableSkillEditorScreen(
    onBack: () -> Unit,
    viewModel: PortableSkillEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_portable_chooser)
    EditorScaffold(
        title = if (viewModel.isEditing) stringResource(R.string.portable_edit_skill) else stringResource(R.string.portable_new_skill),
        onBack = onBack,
        onSave = { viewModel.save(onBack) },
        onShare = {
            val json = viewModel.exportJson() ?: return@EditorScaffold
            runCatching {
                sharePortableDocument(context, json, PortableKind.SKILL, state.title.ifBlank { state.name }, shareTitle)
            }
        },
        onDelete = if (viewModel.isEditing) {
            { viewModel.delete(onBack) }
        } else {
            null
        }
    ) {
        if (state.untrusted) UntrustedNote()
        if (state.missing) {
            Text(stringResource(R.string.portable_missing))
        } else if (!state.loading) {
            EditorField(state.name, stringResource(R.string.portable_skill_name)) { value ->
                viewModel.update { it.copy(name = value) }
            }
            EditorField(state.title, stringResource(R.string.portable_skill_title)) { value ->
                viewModel.update { it.copy(title = value) }
            }
            EditorField(state.description, stringResource(R.string.portable_skill_description), singleLine = false) { value ->
                viewModel.update { it.copy(description = value) }
            }
            EditorField(state.instructions, stringResource(R.string.portable_skill_instructions), singleLine = false) { value ->
                viewModel.update { it.copy(instructions = value) }
            }
            EditorField(state.tools, stringResource(R.string.portable_skill_tools), singleLine = false) { value ->
                viewModel.update { it.copy(tools = value) }
            }
            Text(
                text = stringResource(R.string.portable_skill_tools_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            EditorField(state.examples, stringResource(R.string.portable_examples), singleLine = false) { value ->
                viewModel.update { it.copy(examples = value) }
            }
            state.error?.let { error ->
                Text(stringResource(editorError(error)), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPromptEditorScreen(
    onBack: () -> Unit,
    viewModel: SavedPromptEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_portable_chooser)
    EditorScaffold(
        title = if (viewModel.isEditing) stringResource(R.string.portable_edit_prompt) else stringResource(R.string.portable_new_prompt),
        onBack = onBack,
        onSave = { viewModel.save(onBack) },
        onShare = {
            val json = viewModel.exportJson() ?: return@EditorScaffold
            runCatching {
                sharePortableDocument(context, json, PortableKind.PROMPT, state.title.ifBlank { "prompt" }, shareTitle)
            }
        },
        onDelete = if (viewModel.isEditing) {
            { viewModel.delete(onBack) }
        } else {
            null
        }
    ) {
        if (state.untrusted) UntrustedNote()
        if (state.missing) {
            Text(stringResource(R.string.portable_missing))
        } else if (!state.loading) {
            EditorField(state.title, stringResource(R.string.prompt_title)) { value ->
                viewModel.update { it.copy(title = value) }
            }
            EditorField(state.text, stringResource(R.string.prompt_text), singleLine = false) { value ->
                viewModel.update { it.copy(text = value) }
            }
            state.error?.let { error ->
                Text(stringResource(editorError(error)), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScaffold(
    title: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDelete: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                actions = {
                    TextButton(onClick = onSave) { Text(stringResource(R.string.save)) }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.bot_actions))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_portable)) },
                            onClick = {
                                menu = false
                                onShare()
                            }
                        )
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                onClick = {
                                    menu = false
                                    confirmDelete = true
                                }
                            )
                        }
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
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.portable_delete_title, title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun UntrustedNote() {
    Text(
        text = stringResource(R.string.portable_imported_banner),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun EditorField(
    value: String,
    label: String,
    singleLine: Boolean = true,
    onValue: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4
    )
}

private fun editorError(error: PortableEditorError): Int = when (error) {
    PortableEditorError.NAME -> R.string.portable_error_name
    PortableEditorError.DESCRIPTION -> R.string.portable_error_description
    PortableEditorError.INSTRUCTIONS -> R.string.portable_error_instructions
    PortableEditorError.TEXT -> R.string.portable_error_text
    PortableEditorError.LIBRARY -> R.string.portable_library_full
}
