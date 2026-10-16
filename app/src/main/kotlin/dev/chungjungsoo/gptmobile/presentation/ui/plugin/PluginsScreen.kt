package dev.chungjungsoo.gptmobile.presentation.ui.plugin

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.PluginEntity
import dev.chungjungsoo.gptmobile.data.plugin.PluginAuth
import dev.chungjungsoo.gptmobile.data.plugin.PluginGroup
import dev.chungjungsoo.gptmobile.data.plugin.PluginOAuthBrowser
import dev.chungjungsoo.gptmobile.data.plugin.PluginPreset
import dev.chungjungsoo.gptmobile.data.plugin.PluginTransport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(
    onNavigationClick: () -> Unit,
    viewModel: PluginsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var customOpen by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state.pendingAuth) {
        val spec = state.pendingAuth ?: return@LaunchedEffect
        PluginOAuthBrowser.launch(context, spec)
        viewModel.clearPendingAuth()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plugins)) },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.plugins_official), style = MaterialTheme.typography.titleMedium)
            PluginCatalog.official.forEach { preset ->
                PluginPresetRow(
                    preset = preset,
                    installed = state.plugins.any { it.catalogId == preset.id },
                    onEnable = { url, token -> viewModel.enablePreset(preset, url, token) }
                )
            }
            Text(stringResource(R.string.plugins_personal), style = MaterialTheme.typography.titleMedium)
            PluginCatalog.personal.forEach { preset ->
                PluginPresetRow(
                    preset = preset,
                    installed = state.plugins.any { it.catalogId == preset.id },
                    onEnable = { url, token -> viewModel.enablePreset(preset, url, token) }
                )
            }
            Button(onClick = { customOpen = true }) {
                Text(stringResource(R.string.plugin_add_custom))
            }
            state.plugins.forEach { plugin ->
                InstalledPluginCard(
                    plugin = plugin,
                    onRefresh = { viewModel.refresh(plugin.pluginUid) },
                    onConnect = { viewModel.connect(plugin.pluginUid) },
                    onDelete = { viewModel.delete(plugin.pluginUid) },
                    onEnabled = { viewModel.setEnabled(plugin.pluginUid, it) },
                    tools = state.tools[plugin.pluginUid].orEmpty(),
                    onToolEnabled = viewModel::setToolEnabled
                )
            }
            Text(stringResource(R.string.plugin_scope_title), style = MaterialTheme.typography.titleMedium)
            if (state.bots.isEmpty() && state.projects.isEmpty()) {
                Text(stringResource(R.string.plugin_scope_unavailable))
            }
            state.bots.forEach { bot ->
                Text(bot.name, style = MaterialTheme.typography.titleSmall)
                ScopeChips(state, bot.uid, "BOT", viewModel)
            }
            state.projects.forEach { project ->
                Text(project.name, style = MaterialTheme.typography.titleSmall)
                ScopeChips(state, project.id, "PROJECT", viewModel)
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (customOpen) {
        CustomPluginDialog(
            onDismiss = { customOpen = false },
            onSave = { name, url, transport, auth, token, cleartext ->
                viewModel.addCustom(name, url, transport, auth, token, cleartext)
                customOpen = false
            }
        )
    }
}

@Composable
fun PluginPresetRow(
    preset: PluginPreset,
    installed: Boolean,
    onEnable: (String, String?) -> Unit
) {
    var url by rememberSaveable(preset.id) { mutableStateOf(preset.endpointUrl) }
    var token by rememberSaveable(preset.id) { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(preset.name, style = MaterialTheme.typography.titleSmall)
        Text(preset.authNote, style = MaterialTheme.typography.bodySmall)
        if (preset.needsServer) {
            Text(stringResource(R.string.plugin_needs_server), style = MaterialTheme.typography.labelMedium)
        }
        if (!preset.verified) {
            Text(stringResource(R.string.plugin_unverified), style = MaterialTheme.typography.labelMedium)
        }
        if (preset.requiresUserToken) {
            Text(stringResource(R.string.plugin_needs_user_token), style = MaterialTheme.typography.labelMedium)
        }
        if (preset.configurableUrl || preset.group == PluginGroup.PERSONAL) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.plugin_url)) },
                placeholder = {
                    if (preset.urlPlaceholder.isNotEmpty()) Text(preset.urlPlaceholder)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (preset.authType == PluginAuth.BEARER || preset.allowsBearerFallback) {
            OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text(stringResource(R.string.plugin_bearer_token)) }, modifier = Modifier.fillMaxWidth())
        }
        Button(
            onClick = { onEnable(url, token.ifBlank { null }) },
            enabled = !installed && (!preset.needsServer || url.isNotBlank())
        ) {
            Text(if (installed) stringResource(R.string.plugin_enabled) else stringResource(R.string.plugin_enable))
        }
    }
}

@Composable
private fun InstalledPluginCard(
    plugin: PluginEntity,
    onRefresh: () -> Unit,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    tools: List<dev.chungjungsoo.gptmobile.data.database.entity.PluginToolEntity>,
    onToolEnabled: (String, Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(plugin.name, style = MaterialTheme.typography.titleSmall)
                Text("${plugin.status}${plugin.statusDetail?.let { " · $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = plugin.enabled, onCheckedChange = onEnabled)
        }
        Row {
            TextButton(onClick = onRefresh) { Text(stringResource(R.string.plugin_refresh)) }
            if (plugin.authType == PluginAuth.OAUTH) {
                TextButton(onClick = onConnect) { Text(stringResource(R.string.plugin_connect)) }
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
        }
        tools.forEach { tool ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tool.name, style = MaterialTheme.typography.bodyMedium)
                    Text(tool.effect, style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = tool.enabled, onCheckedChange = { onToolEnabled(tool.toolUid, it) })
            }
        }
    }
}

@Composable
private fun ScopeChips(
    state: PluginsUiState,
    scopeId: String,
    scopeType: String,
    viewModel: PluginsViewModel
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.plugins.forEach { plugin ->
            val selected = state.selections.any { it.scopeType == scopeType && it.scopeId == scopeId && it.pluginUid == plugin.pluginUid }
            FilterChip(
                selected = selected,
                onClick = { viewModel.setScope(scopeType, scopeId, plugin.pluginUid, !selected) },
                label = { Text(plugin.name) }
            )
        }
    }
}

@Composable
private fun CustomPluginDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String?, Boolean) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("https://") }
    var transport by rememberSaveable { mutableStateOf(PluginTransport.STREAMABLE_HTTP) }
    var auth by rememberSaveable { mutableStateOf(PluginAuth.NONE) }
    var token by rememberSaveable { mutableStateOf("") }
    var cleartext by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugin_add_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name)) })
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.plugin_url)) })
                Row {
                    FilterChip(selected = transport == PluginTransport.STREAMABLE_HTTP, onClick = { transport = PluginTransport.STREAMABLE_HTTP }, label = { Text("HTTP") })
                    FilterChip(selected = transport == PluginTransport.SSE, onClick = { transport = PluginTransport.SSE }, label = { Text("SSE") })
                }
                Row {
                    FilterChip(selected = auth == PluginAuth.NONE, onClick = { auth = PluginAuth.NONE }, label = { Text(stringResource(R.string.plugin_auth_none)) })
                    FilterChip(selected = auth == PluginAuth.BEARER, onClick = { auth = PluginAuth.BEARER }, label = { Text(stringResource(R.string.plugin_auth_bearer)) })
                    FilterChip(selected = auth == PluginAuth.OAUTH, onClick = { auth = PluginAuth.OAUTH }, label = { Text(stringResource(R.string.plugin_auth_oauth)) })
                }
                if (auth == PluginAuth.BEARER) {
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text(stringResource(R.string.plugin_bearer_token)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.plugin_allow_cleartext))
                    Switch(checked = cleartext, onCheckedChange = { cleartext = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, url, transport, auth, token.ifBlank { null }, cleartext) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private object PluginCatalog {
    val official = dev.chungjungsoo.gptmobile.data.plugin.PluginCatalog.officialPresets
    val personal = dev.chungjungsoo.gptmobile.data.plugin.PluginCatalog.personalPresets
}
