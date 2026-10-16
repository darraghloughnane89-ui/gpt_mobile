package dev.chungjungsoo.gptmobile.presentation.ui.provider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.provider.OpenAiCompatiblePresets
import dev.chungjungsoo.gptmobile.data.provider.RemoteModel

@Composable
fun RemoteModelPicker(
    query: String,
    onQueryChange: (String) -> Unit,
    manualModel: String,
    onManualModelChange: (String) -> Unit,
    models: List<RemoteModel>,
    isLoading: Boolean,
    statusMessage: String?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = models.filter { model ->
        query.isBlank() || model.id.contains(query, ignoreCase = true)
    }.take(40)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = manualModel,
            onValueChange = onManualModelChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_model_manual"),
            label = { Text(stringResource(R.string.model_id_override)) },
            singleLine = true,
            supportingText = { Text(stringResource(R.string.model_id_override_supporting)) }
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("remote_model_search"),
            label = { Text(stringResource(R.string.search_models)) },
            singleLine = true
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onRefresh, enabled = !isLoading) {
                Text(stringResource(R.string.refresh_models))
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
            }
        }
        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (visible.isEmpty()) {
                Text(
                    text = stringResource(R.string.model_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            visible.forEach { model ->
                TextButton(
                    onClick = { onManualModelChange(model.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("remote_model_${model.id}")
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(text = model.id, style = MaterialTheme.typography.bodyLarge)
                        if (model.supportsVision) {
                            Text(
                                text = stringResource(R.string.capability_vision),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteModelPickerField(
    clientType: ClientType,
    apiUrl: String,
    token: String?,
    model: String,
    onModelChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    catalog: RemoteModelCatalogViewModel = hiltViewModel()
) {
    val key = catalog.key(clientType, apiUrl)
    val state by catalog.states.collectAsStateWithLifecycle()
    val entry = state[key] ?: RemoteCatalogState(models = catalog.defaults(clientType))
    var query by rememberSaveable(key) { mutableStateOf("") }
    LaunchedEffect(key, token) {
        catalog.ensureLoaded(clientType, apiUrl, token)
    }
    RemoteModelPicker(
        query = query,
        onQueryChange = { query = it },
        manualModel = model,
        onManualModelChange = onModelChange,
        models = entry.models,
        isLoading = entry.isLoading,
        statusMessage = entry.message,
        onRefresh = { catalog.refresh(clientType, apiUrl, token) },
        modifier = modifier
    )
}

@Composable
fun EndpointPresetChips(
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.openai_compatible_presets),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(R.string.openai_compatible_presets_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        OpenAiCompatiblePresets.shortcuts.forEach { preset ->
            TextButton(onClick = { onSelected(preset.apiUrl) }) {
                Text("${preset.label} · ${preset.apiUrl}")
            }
        }
    }
}

@Composable
fun ModelFetchProgress(visible: Boolean, modifier: Modifier = Modifier) {
    if (visible) {
        LinearProgressIndicator(modifier = modifier.fillMaxWidth())
    }
}
