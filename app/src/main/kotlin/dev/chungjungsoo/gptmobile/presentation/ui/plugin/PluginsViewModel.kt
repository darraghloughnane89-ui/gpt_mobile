package dev.chungjungsoo.gptmobile.presentation.ui.plugin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.dao.PluginRoutineDao
import dev.chungjungsoo.gptmobile.data.database.entity.PluginEntity
import dev.chungjungsoo.gptmobile.data.database.entity.PluginScopeSelectionEntity
import dev.chungjungsoo.gptmobile.data.database.entity.PluginToolEntity
import dev.chungjungsoo.gptmobile.data.plugin.AppAuthLaunchSpec
import dev.chungjungsoo.gptmobile.data.plugin.BotDirectory
import dev.chungjungsoo.gptmobile.data.plugin.BotSummary
import dev.chungjungsoo.gptmobile.data.plugin.PluginPreset
import dev.chungjungsoo.gptmobile.data.plugin.PluginRepository
import dev.chungjungsoo.gptmobile.data.plugin.ProjectDirectory
import dev.chungjungsoo.gptmobile.data.plugin.ProjectSummary
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PluginsUiState(
    val plugins: List<PluginEntity> = emptyList(),
    val tools: Map<String, List<PluginToolEntity>> = emptyMap(),
    val selections: List<PluginScopeSelectionEntity> = emptyList(),
    val bots: List<BotSummary> = emptyList(),
    val projects: List<ProjectSummary> = emptyList(),
    val message: String? = null,
    val pendingAuth: AppAuthLaunchSpec? = null
)

@HiltViewModel
class PluginsViewModel @Inject constructor(
    private val repository: PluginRepository,
    private val dao: PluginRoutineDao,
    private val bots: BotDirectory,
    private val projects: ProjectDirectory
) : ViewModel() {
    private val _state = MutableStateFlow(PluginsUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observePlugins().collect { plugins ->
                val tools = plugins.associate { it.pluginUid to repository.tools(it.pluginUid) }
                _state.update { it.copy(plugins = plugins, tools = tools, selections = dao.listSelections()) }
            }
        }
        viewModelScope.launch {
            _state.update { it.copy(bots = bots.bots(), projects = projects.projects()) }
        }
    }

    fun enablePreset(preset: PluginPreset, url: String, token: String?) {
        viewModelScope.launch {
            runCatching {
                val plugin = repository.installPreset(preset, url.ifBlank { preset.endpointUrl }, token)
                if (plugin.authType == dev.chungjungsoo.gptmobile.data.plugin.PluginAuth.OAUTH) {
                    _state.update { it.copy(pendingAuth = repository.beginOAuth(plugin.pluginUid), message = null) }
                }
            }.onFailure { error -> _state.update { it.copy(message = error.message) } }
        }
    }

    fun addCustom(name: String, url: String, transport: String, auth: String, token: String?, cleartext: Boolean) {
        viewModelScope.launch {
            runCatching { repository.installCustom(name, url, transport, auth, token, cleartext) }
                .onFailure { error -> _state.update { it.copy(message = error.message) } }
        }
    }

    fun refresh(pluginUid: String) {
        viewModelScope.launch {
            runCatching { repository.refresh(pluginUid) }
                .onFailure { error -> _state.update { it.copy(message = error.message) } }
        }
    }

    fun connect(pluginUid: String) {
        viewModelScope.launch {
            runCatching { repository.beginOAuth(pluginUid) }
                .onSuccess { spec -> _state.update { it.copy(pendingAuth = spec, message = null) } }
                .onFailure { error -> _state.update { it.copy(message = error.message) } }
        }
    }

    fun clearPendingAuth() {
        _state.update { it.copy(pendingAuth = null) }
    }

    fun setEnabled(pluginUid: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(pluginUid, enabled) }
    }

    fun setToolEnabled(toolUid: String, enabled: Boolean) {
        viewModelScope.launch { repository.setToolEnabled(toolUid, enabled) }
    }

    fun delete(pluginUid: String) {
        viewModelScope.launch { repository.delete(pluginUid) }
    }

    fun setScope(scopeType: String, scopeId: String, pluginUid: String, selected: Boolean) {
        viewModelScope.launch {
            repository.setScope(scopeType, scopeId, pluginUid, selected)
            _state.update { it.copy(selections = dao.listSelections()) }
        }
    }
}
