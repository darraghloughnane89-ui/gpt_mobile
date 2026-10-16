package dev.chungjungsoo.gptmobile.presentation.ui.canvas

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.canvas.CanvasDiffKind
import dev.chungjungsoo.gptmobile.data.canvas.CanvasDocument
import dev.chungjungsoo.gptmobile.data.canvas.CanvasExportFormat
import dev.chungjungsoo.gptmobile.data.canvas.CanvasKind
import dev.chungjungsoo.gptmobile.data.canvas.CanvasProposal
import dev.chungjungsoo.gptmobile.data.canvas.CanvasVersion
import dev.chungjungsoo.gptmobile.data.canvas.CanvasVersionSource
import dev.chungjungsoo.gptmobile.data.canvas.diffLines
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatMarkdown
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

data class CanvasTextSelection(
    val start: Int,
    val end: Int,
    val text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasTopBar(
    document: CanvasDocument,
    documents: List<CanvasDocument>,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onExport: (CanvasExportFormat) -> Unit,
    onSelectDocument: (String) -> Unit
) {
    var exportOpen by remember { mutableStateOf(false) }
    var documentsOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Column {
                Text(document.title, maxLines = 1)
                Text(
                    text = if (document.kind == CanvasKind.Markdown) {
                        stringResource(R.string.canvas_kind_markdown)
                    } else {
                        document.language
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.canvas_close))
            }
        },
        actions = {
            if (documents.size > 1) {
                IconButton(onClick = { documentsOpen = true }) {
                    Icon(Icons.Outlined.Description, contentDescription = stringResource(R.string.canvas_documents))
                }
                DropdownMenu(expanded = documentsOpen, onDismissRequest = { documentsOpen = false }) {
                    documents.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.title) },
                            onClick = {
                                documentsOpen = false
                                onSelectDocument(item.id)
                            }
                        )
                    }
                }
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.canvas_copy))
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.canvas_share))
            }
            IconButton(onClick = { exportOpen = true }) {
                Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(R.string.canvas_export))
            }
            DropdownMenu(expanded = exportOpen, onDismissRequest = { exportOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.canvas_export_markdown)) },
                    onClick = {
                        exportOpen = false
                        onExport(CanvasExportFormat.Markdown)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.canvas_export_text)) },
                    onClick = {
                        exportOpen = false
                        onExport(CanvasExportFormat.PlainText)
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.canvas_export_pdf)) },
                    onClick = {
                        exportOpen = false
                        onExport(CanvasExportFormat.Pdf)
                    }
                )
            }
        }
    )
}

@Composable
fun CanvasActionBar(
    selection: CanvasTextSelection?,
    proposal: CanvasProposal?,
    onOpenChat: () -> Unit,
    onRewriteSelection: (CanvasTextSelection) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (proposal != null) {
                Text(
                    text = stringResource(R.string.canvas_proposal),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onReject) {
                    Text(stringResource(R.string.canvas_reject))
                }
                Button(onClick = onAccept) {
                    Text(stringResource(R.string.canvas_accept))
                }
            } else {
                TextButton(onClick = onOpenChat) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null)
                    Text(
                        text = stringResource(R.string.canvas_chat),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                TextButton(
                    enabled = selection != null,
                    onClick = { selection?.let(onRewriteSelection) }
                ) {
                    Text(stringResource(R.string.canvas_rewrite_selection))
                }
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
fun CanvasEditorPane(
    document: CanvasDocument,
    proposal: CanvasProposal?,
    onContentChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onSelectionChange: (CanvasTextSelection?) -> Unit,
    onRestoreVersion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var historyOpen by remember { mutableStateOf(false) }
    var previewing by remember(document.id) { mutableStateOf(false) }
    var title by remember(document.id) { mutableStateOf(document.title) }
    val editor = remember(document.id) { TextFieldState(document.content) }

    LaunchedEffect(document.title) {
        if (title != document.title) title = document.title
    }
    LaunchedEffect(document.activeVersionId, document.content) {
        if (editor.text.toString() != document.content) {
            editor.setTextAndPlaceCursorAtEnd(document.content)
        }
    }
    LaunchedEffect(editor) {
        snapshotFlow { editor.text.toString() }
            .drop(1)
            .debounce(400)
            .collect { text -> onContentChange(text) }
    }
    LaunchedEffect(title) {
        delay(300)
        if (title != document.title) onTitleChange(title)
    }

    val selection = editor.selection
    val selected = if (selection.collapsed) {
        null
    } else {
        val text = editor.text.subSequence(selection.min, selection.max).toString()
        if (text.isBlank()) null else CanvasTextSelection(selection.min, selection.max, text)
    }
    LaunchedEffect(selected) {
        onSelectionChange(selected)
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium,
            label = { Text(stringResource(R.string.canvas_title)) }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { historyOpen = true }) {
                Icon(Icons.Outlined.History, contentDescription = null)
                Text(
                    text = stringResource(R.string.canvas_versions),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            if (document.kind == CanvasKind.Markdown && proposal == null) {
                TextButton(onClick = { previewing = !previewing }) {
                    Text(
                        if (previewing) {
                            stringResource(R.string.canvas_edit)
                        } else {
                            stringResource(R.string.canvas_preview)
                        }
                    )
                }
            }
        }
        when {
            proposal != null -> CanvasDiffView(
                before = document.content,
                after = proposal.proposedContent,
                modifier = Modifier.weight(1f)
            )

            previewing && document.kind == CanvasKind.Markdown -> ChatMarkdown(
                content = editor.text.toString(),
                contentIdentity = document.activeVersionId,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            )

            else -> BasicTextField(
                state = editor,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = if (document.kind == CanvasKind.Code) FontFamily.Monospace else FontFamily.Default,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                lineLimits = TextFieldLineLimits.MultiLine(),
                decorator = { inner ->
                    Box {
                        if (editor.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.canvas_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }

    if (historyOpen) {
        CanvasVersionSheet(
            versions = document.versions,
            activeVersionId = document.activeVersionId,
            onRestore = {
                historyOpen = false
                onRestoreVersion(it)
            },
            onDismiss = { historyOpen = false }
        )
    }
}

@Composable
fun CanvasDiffView(
    before: String,
    after: String,
    modifier: Modifier = Modifier
) {
    val lines = remember(before, after) { diffLines(before, after) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        lines.forEach { line ->
            val background = when (line.kind) {
                CanvasDiffKind.Same -> MaterialTheme.colorScheme.surface
                CanvasDiffKind.Added -> MaterialTheme.colorScheme.primaryContainer
                CanvasDiffKind.Removed -> MaterialTheme.colorScheme.errorContainer
            }
            val prefix = when (line.kind) {
                CanvasDiffKind.Same -> "  "
                CanvasDiffKind.Added -> "+ "
                CanvasDiffKind.Removed -> "- "
            }
            Text(
                text = prefix + line.text.ifEmpty { " " },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(background)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                textDecoration = if (line.kind == CanvasDiffKind.Removed) TextDecoration.LineThrough else null,
                color = when (line.kind) {
                    CanvasDiffKind.Removed -> MaterialTheme.colorScheme.onErrorContainer
                    CanvasDiffKind.Added -> MaterialTheme.colorScheme.onPrimaryContainer
                    CanvasDiffKind.Same -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanvasVersionSheet(
    versions: List<CanvasVersion>,
    activeVersionId: String,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.canvas_versions),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            versions.asReversed().forEach { version ->
                val label = when (version.source) {
                    CanvasVersionSource.Open -> stringResource(R.string.canvas_version_open)
                    CanvasVersionSource.User -> stringResource(R.string.canvas_version_user)
                    CanvasVersionSource.Model -> stringResource(R.string.canvas_version_model)
                }
                val whenLabel = DateUtils.getRelativeTimeSpanString(version.createdAt).toString()
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    Text(whenLabel, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = version.content.lineSequence().firstOrNull().orEmpty().ifBlank {
                            stringResource(R.string.canvas_empty)
                        },
                        maxLines = 2,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (version.id != activeVersionId) {
                        TextButton(onClick = { onRestore(version.id) }) {
                            Text(stringResource(R.string.canvas_restore))
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
