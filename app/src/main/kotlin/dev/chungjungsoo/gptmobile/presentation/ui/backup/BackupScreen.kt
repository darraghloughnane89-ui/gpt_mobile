package dev.chungjungsoo.gptmobile.presentation.ui.backup

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.ui.privacy.AppLockAuthenticator
import dev.chungjungsoo.gptmobile.presentation.ui.privacy.PrivacyScaffold

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalView.current.context as? FragmentActivity
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importFrom(uri)
    }
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onConsentFinished(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(state.consentIntent) {
        val pending = state.consentIntent ?: return@LaunchedEffect
        consentLauncher.launch(IntentSenderRequest.Builder(pending).build())
        viewModel.consentDelivered()
    }
    LaunchedEffect(state.unlockRequested) {
        if (!state.unlockRequested) return@LaunchedEffect
        val host = activity
        if (host == null) {
            viewModel.onVaultUnlocked(false)
            return@LaunchedEffect
        }
        AppLockAuthenticator.authenticate(
            activity = host,
            title = host.getString(R.string.vault_unlock_title),
            subtitle = host.getString(R.string.backup_unlock_required),
            onResult = viewModel::onVaultUnlocked
        )
    }

    PrivacyScaffold(
        title = stringResource(R.string.backup_title),
        onBack = onBack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.backup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            if (state.notice.isNotEmpty()) {
                Text(
                    text = state.notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.noticeIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            BackupButton(
                text = stringResource(R.string.backup_export),
                enabled = !state.busy,
                onClick = { exportLauncher.launch("gptmobile-export.json") }
            )
            BackupButton(
                text = stringResource(R.string.backup_import),
                enabled = !state.busy,
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
            )
            BackupButton(
                text = stringResource(R.string.backup_drive_backup),
                enabled = !state.busy,
                onClick = viewModel::backupToDrive
            )
            BackupButton(
                text = stringResource(R.string.backup_drive_restore),
                enabled = !state.busy,
                onClick = viewModel::restoreFromDrive
            )
        }
    }
}

@Composable
private fun BackupButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(text)
    }
}
