package dev.chungjungsoo.gptmobile.presentation.ui.gguf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.gguf.GgufCatalog
import dev.chungjungsoo.gptmobile.data.gguf.GgufCatalogEntry
import dev.chungjungsoo.gptmobile.data.gguf.GgufDownloadSnapshot
import dev.chungjungsoo.gptmobile.data.gguf.GgufDownloadState
import dev.chungjungsoo.gptmobile.data.gguf.GgufModelRecord
import dev.chungjungsoo.gptmobile.presentation.common.EmptyErrorState
import dev.chungjungsoo.gptmobile.presentation.common.SettingsSection
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GgufModelsScreen(
    modifier: Modifier = Modifier,
    viewModel: GgufModelsViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    var downloadUrl by rememberSaveable { mutableStateOf("") }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importUri(context, uri)
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Text(
                        text = stringResource(R.string.gguf_models),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp)
                .testTag("gguf_models_screen"),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection(title = stringResource(R.string.gguf_suggested_models)) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .testTag("gguf_catalog"),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.gguf_suggested_models_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GgufCatalog.entries.forEach { entry ->
                        GgufCatalogRow(
                            entry = entry,
                            installed = uiState.models.any { model ->
                                model.catalogId == entry.id || model.sha256.equals(entry.sha256, ignoreCase = true)
                            },
                            download = uiState.downloads.firstOrNull { it.downloadKey == entry.id },
                            partialBytes = uiState.partialBytes[entry.id] ?: 0L,
                            onDownload = { viewModel.downloadCatalog(entry.id) },
                            onCancel = { viewModel.cancelDownload(entry.id) },
                            onDiscard = { viewModel.discardPartial(entry.id) }
                        )
                    }
                }
            }
            SettingsSection(title = stringResource(R.string.gguf_add_model)) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.gguf_add_model_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.gguf_import))
                    }
                    OutlinedTextField(
                        value = downloadUrl,
                        onValueChange = { downloadUrl = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gguf_hf_url"),
                        label = { Text(stringResource(R.string.gguf_download_url)) },
                        singleLine = true
                    )
                    val pasted = uiState.downloads.filter { snapshot ->
                        snapshot.downloadKey.startsWith("hf-") &&
                            (snapshot.state == GgufDownloadState.ENQUEUED || snapshot.state == GgufDownloadState.RUNNING)
                    }
                    Row {
                        TextButton(
                            onClick = { viewModel.downloadUrl(downloadUrl) },
                            enabled = pasted.isEmpty()
                        ) {
                            Text(stringResource(R.string.gguf_download))
                        }
                    }
                    pasted.forEach { snapshot ->
                        GgufDownloadProgress(
                            snapshot = snapshot,
                            totalHint = snapshot.totalBytes,
                            onCancel = { viewModel.cancelDownload(snapshot.downloadKey) }
                        )
                    }
                }
            }
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            } else if (uiState.models.isEmpty()) {
                EmptyErrorState(
                    title = stringResource(R.string.gguf_empty_title),
                    description = stringResource(R.string.gguf_empty_description)
                )
            } else {
                uiState.models.forEach { record ->
                    SettingsSection(title = record.displayName) {
                        GgufModelRow(
                            record = record,
                            isDefault = record.id == uiState.defaultModelId,
                            onParameters = { viewModel.beginEdit(record) },
                            onSetDefault = { viewModel.setDefault(record.id) },
                            onDelete = { viewModel.delete(record.id) }
                        )
                    }
                }
            }
        }
    }

    uiState.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            title = { Text(stringResource(R.string.gguf_models)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::consumeMessage) {
                    Text(stringResource(R.string.confirm))
                }
            }
        )
    }
    uiState.editing?.let { record ->
        GgufParametersDialog(
            record = record,
            onDismiss = viewModel::dismissEdit,
            onSave = viewModel::saveParameters
        )
    }
    uiState.pendingConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDownloadConfirmation,
            title = { Text(stringResource(R.string.gguf_models)) },
            text = { Text(pending.warning) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDownload) {
                    Text(stringResource(R.string.gguf_download_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDownloadConfirmation) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun GgufCatalogRow(
    entry: GgufCatalogEntry,
    installed: Boolean,
    download: GgufDownloadSnapshot?,
    partialBytes: Long,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDiscard: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val activeDownload = download?.takeIf {
        it.state == GgufDownloadState.RUNNING || it.state == GgufDownloadState.ENQUEUED
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = entry.displayName, style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(
                R.string.gguf_catalog_meta,
                entry.parameterLabel,
                entry.quantization,
                formatBytes(entry.sizeBytes)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = entry.sourceNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { uriHandler.openUri(entry.licenseUrl) }) {
            Text(stringResource(R.string.gguf_license, entry.licenseName))
        }
        when {
            installed -> Text(
                text = stringResource(R.string.gguf_on_device),
                style = MaterialTheme.typography.bodyMedium
            )

            activeDownload != null -> GgufDownloadProgress(
                snapshot = activeDownload,
                totalHint = entry.sizeBytes,
                onCancel = onCancel
            )

            partialBytes > 0L -> Row {
                TextButton(onClick = onDownload) { Text(stringResource(R.string.gguf_resume)) }
                TextButton(onClick = onDiscard) { Text(stringResource(R.string.gguf_discard_partial)) }
            }

            else -> TextButton(onClick = onDownload) { Text(stringResource(R.string.gguf_download)) }
        }
    }
}

@Composable
private fun GgufDownloadProgress(
    snapshot: GgufDownloadSnapshot,
    totalHint: Long,
    onCancel: () -> Unit
) {
    val total = snapshot.totalBytes.takeIf { it > 0L } ?: totalHint
    Column {
        if (total > 0L) {
            LinearProgressIndicator(
                progress = { (snapshot.receivedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.gguf_stop_download)) }
    }
}

@Composable
private fun GgufModelRow(
    record: GgufModelRecord,
    isDefault: Boolean,
    onParameters: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.gguf_model_summary, record.contextSize, record.temperature, formatBytes(record.sizeBytes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        record.licenseName?.let { license ->
            Text(
                text = stringResource(R.string.gguf_license, license),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isDefault) {
            Text(
                text = stringResource(R.string.gguf_default_offline),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row {
            TextButton(onClick = onParameters) { Text(stringResource(R.string.gguf_parameters)) }
            if (!isDefault) {
                TextButton(onClick = onSetDefault) { Text(stringResource(R.string.gguf_set_default)) }
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.remove)) }
        }
    }
}

@Composable
private fun GgufParametersDialog(
    record: GgufModelRecord,
    onDismiss: () -> Unit,
    onSave: (Int, Float) -> Unit
) {
    var contextText by rememberSaveable(record.id) { mutableStateOf(record.contextSize.toString()) }
    var temperatureText by rememberSaveable(record.id) { mutableStateOf(record.temperature.toString()) }
    val contextValue = contextText.toIntOrNull()
    val temperatureValue = temperatureText.toFloatOrNull()
    val valid = contextValue != null && contextValue > 0 && temperatureValue != null && temperatureValue in 0f..2f
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gguf_parameters)) },
        text = {
            Column {
                Text(stringResource(R.string.gguf_parameters_description))
                OutlinedTextField(
                    value = contextText,
                    onValueChange = { contextText = it },
                    label = { Text(stringResource(R.string.gguf_context)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = temperatureText,
                    onValueChange = { temperatureText = it },
                    label = { Text(stringResource(R.string.temperature)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(contextValue!!, temperatureValue!!) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun GgufModelChoices(
    models: List<GgufModelRecord>,
    selectedId: String,
    onSelected: (GgufModelRecord) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
    defaultModelId: String? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (models.isEmpty()) {
            Text(
                text = stringResource(R.string.gguf_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        models.forEach { model ->
            TextButton(onClick = { onSelected(model) }, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(model.displayName)
                    Text(
                        text = when {
                            model.id == selectedId && model.id == defaultModelId -> stringResource(R.string.gguf_selected_default)
                            model.id == selectedId -> stringResource(R.string.gguf_selected)
                            model.id == defaultModelId -> stringResource(R.string.gguf_default_offline)
                            else -> stringResource(R.string.gguf_model_summary, model.contextSize, model.temperature, formatBytes(model.sizeBytes))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        TextButton(onClick = onManage) {
            Text(stringResource(R.string.gguf_manage))
        }
    }
}

@Composable
fun SelectDefaultGgufWhenUnset(
    defaultModelId: String?,
    models: List<GgufModelRecord>,
    selectedId: String,
    onSelect: (GgufModelRecord) -> Unit
) {
    LaunchedEffect(defaultModelId, models, selectedId) {
        if (selectedId.isNotBlank() || defaultModelId.isNullOrBlank()) return@LaunchedEffect
        val record = models.firstOrNull { it.id == defaultModelId } ?: return@LaunchedEffect
        onSelect(record)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes.toDouble() / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024.0
        unit += 1
    }
    return "%.1f %s".format(value, units[unit])
}
