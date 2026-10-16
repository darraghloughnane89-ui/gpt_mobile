package dev.chungjungsoo.gptmobile.presentation.ui.obsidian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.obsidian.ConflictChoice
import dev.chungjungsoo.gptmobile.data.obsidian.ObsidianSyncConfig
import dev.chungjungsoo.gptmobile.data.obsidian.ObsidianSyncEngine
import dev.chungjungsoo.gptmobile.data.obsidian.ObsidianSyncIndexStore
import dev.chungjungsoo.gptmobile.data.obsidian.ObsidianSyncPreferences
import dev.chungjungsoo.gptmobile.data.obsidian.VaultBackend
import dev.chungjungsoo.gptmobile.data.obsidian.VaultMarkdown
import dev.chungjungsoo.gptmobile.data.obsidian.github.ObsidianGitHubTokenStore
import dev.chungjungsoo.gptmobile.data.obsidian.local.localFolderLabel
import dev.chungjungsoo.gptmobile.data.obsidian.worker.ObsidianSyncScheduler
import dev.chungjungsoo.gptmobile.util.Brand
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ObsidianSyncViewModel @Inject constructor(
    private val engine: ObsidianSyncEngine,
    private val preferences: ObsidianSyncPreferences,
    private val indexStore: ObsidianSyncIndexStore,
    private val tokenStore: ObsidianGitHubTokenStore,
    private val scheduler: ObsidianSyncScheduler
) : ViewModel() {
    private val _state = MutableStateFlow(ObsidianSyncUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun onOwnerChange(value: String) = _state.update { it.copy(githubOwner = value) }

    fun onRepoChange(value: String) = _state.update { it.copy(githubRepo = value) }

    fun onBranchChange(value: String) = _state.update { it.copy(githubBranch = value) }

    fun onRootChange(value: String) = _state.update { it.copy(rootFolder = value) }

    fun onTokenChange(value: String) = _state.update { it.copy(tokenDraft = value) }

    fun onShowFolderOption() = _state.update { it.copy(showFolderOption = true) }

    fun saveGitHub() {
        viewModelScope.launch {
            val current = _state.value
            if (current.githubOwner.isBlank() || current.githubRepo.isBlank() || current.githubBranch.isBlank()) {
                _state.update { it.copy(status = "Owner, repository, and branch are required.") }
                return@launch
            }
            val existing = preferences.load()
            preferences.save(current.toConfig(VaultBackend.GITHUB).copy(localTreeUri = existing.localTreeUri))
            if (current.tokenDraft.isNotBlank()) {
                tokenStore.save(current.tokenDraft.trim())
            }
            scheduler.ensurePeriodic(VaultBackend.GITHUB)
            _state.update { it.copy(tokenDraft = "", backend = VaultBackend.GITHUB) }
            syncNow()
        }
    }

    fun useFolder(uri: String) {
        viewModelScope.launch {
            val current = preferences.load()
            preferences.save(
                current.copy(
                    backend = VaultBackend.LOCAL.storageKey,
                    localTreeUri = uri,
                    rootFolder = _state.value.rootFolder.ifBlank { Brand.VAULT_ROOT }
                )
            )
            scheduler.ensurePeriodic(VaultBackend.LOCAL)
            load()
        }
    }

    fun useGitHub() {
        viewModelScope.launch {
            val current = preferences.load()
            preferences.save(current.copy(backend = VaultBackend.GITHUB.storageKey))
            load()
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, status = "Syncing…") }
            val report = engine.sync()
            _state.update { it.copy(syncing = false, status = report.message) }
            load()
        }
    }

    fun keepPhone(id: String) = resolve(id, ConflictChoice.KEEP_LOCAL)

    fun keepVault(id: String) = resolve(id, ConflictChoice.KEEP_REMOTE)

    fun keepBoth(id: String) = resolve(id, ConflictChoice.KEEP_BOTH)

    private fun resolve(id: String, choice: ConflictChoice) {
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            val report = engine.resolve(id, choice)
            _state.update { it.copy(syncing = false, status = report.message) }
            load()
        }
    }

    private suspend fun load() {
        val config = preferences.load()
        val index = indexStore.load()
        val tokenSaved = tokenStore.hasToken()
        _state.update { current ->
            current.copy(
                backend = config.vaultBackend,
                rootFolder = config.rootFolder,
                githubOwner = config.githubOwner,
                githubRepo = config.githubRepo,
                githubBranch = config.githubBranch,
                tokenSaved = tokenSaved,
                folderLabel = localFolderLabel(config.localTreeUri),
                status = if (current.syncing) current.status else index.lastStatus,
                conflicts = index.conflicts.map { conflict ->
                    val local = VaultMarkdown.parse(conflict.localMarkdown)
                    val remote = VaultMarkdown.parse(conflict.remoteMarkdown)
                    ConflictUi(
                        id = conflict.id,
                        title = local?.title?.ifBlank { conflict.path } ?: conflict.path,
                        localPreview = preview(local?.body, local?.messages?.joinToString("\n") { it.content }),
                        remotePreview = preview(remote?.body, remote?.messages?.joinToString("\n") { it.content }),
                        allowKeepBoth = conflict.allowKeepBoth
                    )
                },
                log = index.log
            )
        }
    }

    private fun preview(body: String?, messages: String?): String {
        val text = messages?.takeIf { it.isNotBlank() } ?: body.orEmpty()
        return text.lineSequence().take(6).joinToString("\n").take(280)
    }

    private fun ObsidianSyncUiState.toConfig(backend: VaultBackend): ObsidianSyncConfig = ObsidianSyncConfig(
        backend = backend.storageKey,
        rootFolder = rootFolder.ifBlank { Brand.VAULT_ROOT },
        githubOwner = githubOwner.trim(),
        githubRepo = githubRepo.trim(),
        githubBranch = githubBranch.trim(),
        localTreeUri = null
    )
}
