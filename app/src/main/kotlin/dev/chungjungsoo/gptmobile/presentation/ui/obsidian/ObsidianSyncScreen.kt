package dev.chungjungsoo.gptmobile.presentation.ui.obsidian

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.obsidian.VaultBackend
import dev.chungjungsoo.gptmobile.util.Brand

data class ConflictUi(
    val id: String,
    val title: String,
    val localPreview: String,
    val remotePreview: String,
    val allowKeepBoth: Boolean
)

data class ObsidianSyncUiState(
    val brandName: String = Brand.NAME,
    val backend: VaultBackend = VaultBackend.GITHUB,
    val rootFolder: String = Brand.VAULT_ROOT,
    val githubOwner: String = Brand.VAULT_GITHUB_OWNER,
    val githubRepo: String = Brand.VAULT_GITHUB_REPO,
    val githubBranch: String = Brand.VAULT_GITHUB_BRANCH,
    val tokenSaved: Boolean = false,
    val tokenDraft: String = "",
    val folderLabel: String? = null,
    val showFolderOption: Boolean = false,
    val status: String = "Not synced yet",
    val syncing: Boolean = false,
    val conflicts: List<ConflictUi> = emptyList(),
    val log: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObsidianSyncScreen(
    state: ObsidianSyncUiState,
    onBack: () -> Unit,
    onOwnerChange: (String) -> Unit,
    onRepoChange: (String) -> Unit,
    onBranchChange: (String) -> Unit,
    onRootChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSaveGitHub: () -> Unit,
    onSyncNow: () -> Unit,
    onShowFolderOption: () -> Unit,
    onUseGitHub: () -> Unit,
    onPickFolder: () -> Unit,
    onKeepPhone: (String) -> Unit,
    onKeepVault: (String) -> Unit,
    onKeepBoth: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.obsidian_sync_title, state.brandName)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.obsidian_sync_intro, state.brandName),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = state.status,
                style = MaterialTheme.typography.titleMedium
            )
            state.conflicts.forEach { conflict ->
                ConflictCard(
                    conflict = conflict,
                    onKeepPhone = { onKeepPhone(conflict.id) },
                    onKeepVault = { onKeepVault(conflict.id) },
                    onKeepBoth = { onKeepBoth(conflict.id) }
                )
            }
            Text(
                text = stringResource(R.string.obsidian_github_heading),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.obsidian_github_default,
                    state.githubOwner,
                    state.githubRepo
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            ObsidianField(stringResource(R.string.obsidian_owner), state.githubOwner, onOwnerChange)
            ObsidianField(stringResource(R.string.obsidian_repo), state.githubRepo, onRepoChange)
            ObsidianField(stringResource(R.string.obsidian_branch), state.githubBranch, onBranchChange)
            ObsidianField(stringResource(R.string.obsidian_root), state.rootFolder, onRootChange)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.tokenDraft,
                onValueChange = onTokenChange,
                label = {
                    Text(
                        if (state.tokenSaved) {
                            stringResource(R.string.obsidian_token_saved)
                        } else {
                            stringResource(R.string.obsidian_token)
                        }
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Text(
                text = stringResource(R.string.obsidian_token_scopes),
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSaveGitHub, enabled = !state.syncing) {
                    Text(stringResource(R.string.obsidian_save_github))
                }
                OutlinedButton(onClick = onSyncNow, enabled = !state.syncing) {
                    Text(stringResource(R.string.obsidian_sync_now))
                }
            }
            if (state.backend == VaultBackend.LOCAL) {
                Text(stringResource(R.string.obsidian_folder_active, state.folderLabel ?: stringResource(R.string.obsidian_folder)))
                TextButton(onClick = onUseGitHub) {
                    Text(stringResource(R.string.obsidian_use_github))
                }
            }
            TextButton(onClick = onShowFolderOption) {
                Text(stringResource(R.string.obsidian_folder_option))
            }
            if (state.showFolderOption) {
                Text(
                    text = stringResource(R.string.obsidian_folder_help),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = onPickFolder) {
                    Text(stringResource(R.string.obsidian_pick_folder))
                }
            }
            if (state.log.isNotEmpty()) {
                Text(stringResource(R.string.obsidian_log), style = MaterialTheme.typography.titleMedium)
                state.log.takeLast(20).forEach { line ->
                    Text(text = line, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: ConflictUi,
    onKeepPhone: () -> Unit,
    onKeepVault: () -> Unit,
    onKeepBoth: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(conflict.title, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.obsidian_phone_copy))
            Text(conflict.localPreview, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.obsidian_vault_copy))
            Text(conflict.remotePreview, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onKeepPhone) { Text(stringResource(R.string.obsidian_keep_phone)) }
                TextButton(onClick = onKeepVault) { Text(stringResource(R.string.obsidian_keep_vault)) }
                if (conflict.allowKeepBoth) {
                    TextButton(onClick = onKeepBoth) { Text(stringResource(R.string.obsidian_keep_both)) }
                }
            }
        }
    }
}

@Composable
private fun ObsidianField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true
    )
}
