package dev.chungjungsoo.gptmobile.presentation.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.library.ChatFolder
import dev.chungjungsoo.gptmobile.data.library.ChatLibraryState
import dev.chungjungsoo.gptmobile.data.library.ChatListFilter
import dev.chungjungsoo.gptmobile.data.library.FolderNameIssue

@Composable
fun ChatLibraryFilterBar(
    library: ChatLibraryState,
    filter: ChatListFilter,
    onFilter: (ChatListFilter) -> Unit,
    onCreateFolder: (String) -> FolderNameIssue?,
    onRenameFolder: (String, String) -> FolderNameIssue?,
    onDeleteFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var naming by remember { mutableStateOf<FolderNaming?>(null) }
    val selectedFolder = (filter as? ChatListFilter.Folder)?.folderId?.let { folderId ->
        library.folders.firstOrNull { it.id == folderId }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryFilterChip(
                label = stringResource(R.string.library_inbox),
                selected = filter == ChatListFilter.Inbox,
                onClick = { onFilter(ChatListFilter.Inbox) }
            )
            LibraryFilterChip(
                label = stringResource(R.string.library_pinned),
                selected = filter == ChatListFilter.Pinned,
                onClick = { onFilter(ChatListFilter.Pinned) }
            )
            LibraryFilterChip(
                label = stringResource(R.string.library_archived),
                selected = filter == ChatListFilter.Archived,
                onClick = { onFilter(ChatListFilter.Archived) }
            )
            library.folders.forEach { folder ->
                LibraryFilterChip(
                    label = folder.name,
                    selected = filter == ChatListFilter.Folder(folder.id),
                    onClick = { onFilter(ChatListFilter.Folder(folder.id)) }
                )
            }
            LibraryFilterChip(
                label = stringResource(R.string.library_new_folder),
                selected = false,
                onClick = { naming = FolderNaming.Create }
            )
        }
        if (selectedFolder != null) {
            Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { naming = FolderNaming.Rename(selectedFolder) }) {
                    Text(stringResource(R.string.library_rename_folder))
                }
                TextButton(onClick = { onDeleteFolder(selectedFolder.id) }) {
                    Text(stringResource(R.string.library_delete_folder))
                }
            }
        }
    }

    naming?.let { request ->
        FolderNameDialog(
            title = stringResource(
                if (request is FolderNaming.Create) R.string.library_new_folder else R.string.library_rename_folder
            ),
            initialName = (request as? FolderNaming.Rename)?.folder?.name.orEmpty(),
            onDismiss = { naming = null },
            onConfirm = { name ->
                val issue = when (request) {
                    FolderNaming.Create -> onCreateFolder(name)
                    is FolderNaming.Rename -> onRenameFolder(request.folder.id, name)
                }
                if (issue == null) naming = null
                issue
            }
        )
    }
}

@Composable
fun ChatLibrarySelectionBar(
    pinLabel: String,
    archiveLabel: String,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onMoveToFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TextButton(onClick = onPin) { Text(pinLabel) }
        TextButton(onClick = onArchive) { Text(archiveLabel) }
        TextButton(onClick = onMoveToFolder) { Text(stringResource(R.string.library_folder)) }
    }
}

@Composable
fun ChatFolderPickerDialog(
    folders: List<ChatFolder>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onCreate: (String) -> FolderNameIssue?
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        FolderNameDialog(
            title = stringResource(R.string.library_new_folder),
            initialName = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                val issue = onCreate(name)
                if (issue == null) onDismiss()
                issue
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_folder)) },
        text = {
            Column {
                TextButton(onClick = { onSelect(null) }) {
                    Text(stringResource(R.string.library_no_folder))
                }
                folders.forEach { folder ->
                    TextButton(onClick = { onSelect(folder.id) }) {
                        Text(folder.name)
                    }
                }
                TextButton(onClick = { creating = true }) {
                    Text(stringResource(R.string.library_new_folder))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LibraryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun FolderNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> FolderNameIssue?
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var issue by remember { mutableStateOf<FolderNameIssue?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = {
                        name = it
                        issue = null
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.library_folder_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                issue?.let { current ->
                    Text(
                        text = folderIssueText(current),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { issue = onConfirm(name) }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun folderIssueText(issue: FolderNameIssue): String = when (issue) {
    FolderNameIssue.Blank -> stringResource(R.string.library_folder_blank)
    FolderNameIssue.Duplicate -> stringResource(R.string.library_folder_duplicate)
    FolderNameIssue.TooLong -> stringResource(R.string.library_folder_too_long)
}

private sealed interface FolderNaming {
    data object Create : FolderNaming

    data class Rename(val folder: ChatFolder) : FolderNaming
}
