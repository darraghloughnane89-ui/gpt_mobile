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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
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
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.gguf_download_url)) },
                        singleLine = true
                    )
                    Row {
                        TextButton(
                            onClick = { viewModel.download(downloadUrl, context.cacheDir) },
                            enabled = uiState.downloadFraction == null
                        ) {
                            Text(stringResource(R.string.gguf_download))
                        }
                        if (uiState.downloadFraction != null) {
                            TextButton(onClick = viewModel::cancelDownload) {
                                Text(stringResource(R.string.gguf_stop_download))
                            }
                        }
                    }
                    uiState.downloadFraction?.let { fraction ->
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text(
                            text = uiState.downloadLabel.orEmpty(),
                            style = MaterialTheme.typography.bodySmall
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
                            onParameters = { viewModel.beginEdit(record) },
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
}

@Composable
private fun GgufModelRow(
    record: GgufModelRecord,
    onParameters: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.gguf_model_summary, record.contextSize, record.temperature, formatBytes(record.sizeBytes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row {
            TextButton(onClick = onParameters) { Text(stringResource(R.string.gguf_parameters)) }
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
    modifier: Modifier = Modifier
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
                        text = if (model.id == selectedId) {
                            stringResource(R.string.gguf_selected)
                        } else {
                            stringResource(R.string.gguf_model_summary, model.contextSize, model.temperature, formatBytes(model.sizeBytes))
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
