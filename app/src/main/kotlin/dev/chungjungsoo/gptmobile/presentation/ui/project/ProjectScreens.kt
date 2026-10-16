package dev.chungjungsoo.gptmobile.presentation.ui.project

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Project
import dev.chungjungsoo.gptmobile.data.project.ProjectColors
import dev.chungjungsoo.gptmobile.data.project.ProjectIcons
import dev.chungjungsoo.gptmobile.presentation.a11y.minimumTouchTarget
import dev.chungjungsoo.gptmobile.presentation.common.EmptyErrorState
import dev.chungjungsoo.gptmobile.presentation.ui.home.SelectPlatformDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onBack: () -> Unit,
    onOpenProject: (Int) -> Unit,
    onCreateProject: () -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel()
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Project?>(null) }
    var importMenu by remember { mutableStateOf(false) }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importZip(uri)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.importFolder(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.projects)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { importMenu = true }) {
                            Text(stringResource(R.string.project_import))
                        }
                        DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.project_import_zip)) },
                                onClick = {
                                    importMenu = false
                                    zipPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.project_import_folder)) },
                                onClick = {
                                    importMenu = false
                                    folderPicker.launch(null)
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateProject,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.project_new)) }
            )
        }
    ) { innerPadding ->
        if (projects.isEmpty()) {
            EmptyErrorState(
                title = stringResource(R.string.projects_empty_title),
                description = stringResource(R.string.projects_empty_description),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                primaryActionLabel = stringResource(R.string.project_new),
                onPrimaryAction = onCreateProject
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(projects, key = { it.id }) { project ->
                    ListItem(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onOpenProject(project.id) },
                        headlineContent = { Text(project.name) },
                        supportingContent = {
                            Text(
                                text = project.instructions.ifBlank { stringResource(R.string.project_no_instructions) }
                            )
                        },
                        leadingContent = { ProjectBadge(project.icon, project.color) },
                        trailingContent = {
                            IconButton(onClick = { pendingDelete = project }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    )
                }
            }
        }
    }

    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.project_delete_title)) },
            text = { Text(stringResource(R.string.project_delete_body, project.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(project)
                        pendingDelete = null
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    transfer?.let { state ->
        ProjectTransferDialog(state = state, onDismiss = viewModel::dismissTransfer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorScreen(
    onBack: () -> Unit,
    onSaved: (Int) -> Unit,
    viewModel: ProjectEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (viewModel.isNew) R.string.project_new else R.string.project_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onSaved) }) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            state.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            ProjectEditorForm(
                name = state.name,
                instructions = state.instructions,
                icon = state.icon,
                color = state.color,
                onNameChange = viewModel::updateName,
                onInstructionsChange = viewModel::updateInstructions,
                onIconChange = viewModel::updateIcon,
                onColorChange = viewModel::updateColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
    onDeleted: () -> Unit,
    onOpenChat: (ChatRoomV2) -> Unit,
    onNewChat: (Int, List<String>) -> Unit,
    viewModel: ProjectDetailViewModel = hiltViewModel()
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val platforms by viewModel.platforms.collectAsStateWithLifecycle()
    val selectedPlatforms by viewModel.selectedPlatforms.collectAsStateWithLifecycle()
    val showPlatformDialog by viewModel.showPlatformDialog.collectAsStateWithLifecycle()
    val showAddChat by viewModel.showAddChat.collectAsStateWithLifecycle()
    val availableChats by viewModel.availableChats.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refreshPlatforms() }
    LaunchedEffect(notice) {
        notice?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeNotice()
        }
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(uri)
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    val current = project
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.name ?: stringResource(R.string.projects), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                actions = {
                    if (current != null) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.project_export)) },
                                onClick = {
                                    menuExpanded = false
                                    exportPicker.launch(projectZipName(current.name))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.project_edit)) },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit(current.id)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    confirmDelete = true
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (current != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val enabled = platforms.filter { it.enabled }.map { it.uid }
                        if (enabled.size == 1) {
                            onNewChat(current.id, enabled)
                        } else {
                            viewModel.openPlatformDialog()
                        }
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_chat)) }
                )
            }
        }
    ) { innerPadding ->
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.project_missing))
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProjectBadge(current.icon, current.color)
                            Text(current.name, style = MaterialTheme.typography.headlineSmall)
                        }
                        Text(stringResource(R.string.project_instructions), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = current.instructions.ifBlank { stringResource(R.string.project_no_instructions) },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                item {
                    SectionHeader(
                        title = stringResource(R.string.project_files),
                        action = stringResource(R.string.project_add_file),
                        onAction = {
                            filePicker.launch(
                                arrayOf(
                                    "text/*",
                                    "text/markdown",
                                    "application/pdf",
                                    "image/*"
                                )
                            )
                        }
                    )
                }
                if (files.isEmpty()) {
                    item { EmptyLine(stringResource(R.string.project_files_empty)) }
                } else {
                    items(files, key = { it.id }) { file ->
                        ListItem(
                            headlineContent = { Text(file.displayName) },
                            supportingContent = { Text(file.mimeType) },
                            leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            trailingContent = {
                                IconButton(onClick = { viewModel.deleteFile(file) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                                }
                            }
                        )
                    }
                }
                item {
                    SectionHeader(
                        title = stringResource(R.string.project_chats),
                        action = stringResource(R.string.project_add_chat),
                        onAction = viewModel::openAddChat
                    )
                }
                if (chats.isEmpty()) {
                    item { EmptyLine(stringResource(R.string.project_chats_empty)) }
                } else {
                    items(chats, key = { it.id }) { chat ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenChat(chat) },
                            headlineContent = { Text(chat.title) },
                            trailingContent = {
                                TextButton(onClick = { viewModel.removeChat(chat.id) }) {
                                    Text(stringResource(R.string.project_remove_chat))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showPlatformDialog && current != null) {
        SelectPlatformDialog(
            platforms = platforms,
            selectedPlatforms = selectedPlatforms,
            onDismissRequest = viewModel::closePlatformDialog,
            onConfirmation = { enabled ->
                viewModel.closePlatformDialog()
                onNewChat(current.id, enabled)
            },
            onPlatformSelect = viewModel::togglePlatform
        )
    }

    if (showAddChat) {
        AlertDialog(
            onDismissRequest = viewModel::closeAddChat,
            title = { Text(stringResource(R.string.project_add_chat)) },
            text = {
                if (availableChats.isEmpty()) {
                    Text(stringResource(R.string.project_no_chats_to_add))
                } else {
                    Column {
                        availableChats.forEach { chat ->
                            Text(
                                text = chat.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .minimumTouchTarget()
                                    .clickable { viewModel.addChat(chat.id) }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::closeAddChat) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.project_delete_title)) },
            text = { Text(stringResource(R.string.project_delete_body, current?.name.orEmpty())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteProject(onDeleted)
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    transfer?.let { state ->
        ProjectTransferDialog(state = state, onDismiss = viewModel::dismissTransfer)
    }
}

@Composable
private fun ProjectTransferDialog(state: ProjectTransferUi, onDismiss: () -> Unit) {
    val fraction = if (state.total > 0) state.completed.toFloat() / state.total.toFloat() else null
    AlertDialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        title = { Text(state.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.detail)
                if (state.running) {
                    if (fraction == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!state.running) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        }
    )
}

private fun projectZipName(name: String): String {
    val cleaned = name.replace(Regex("[^A-Za-z0-9._ -]"), "").trim().ifBlank { "project" }
    return "$cleaned.zip"
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun ProjectBadge(icon: String, color: Int) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ProjectIcons.image(icon),
            contentDescription = null,
            tint = ProjectColors.color(color)
        )
    }
}

@Composable
fun ProjectsEntryRow(count: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        headlineContent = { Text(stringResource(R.string.projects)) },
        supportingContent = { Text(stringResource(R.string.projects_count, count)) },
        leadingContent = { ProjectBadge(ProjectIcons.FOLDER, ProjectColors.palette.first()) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    )
}
