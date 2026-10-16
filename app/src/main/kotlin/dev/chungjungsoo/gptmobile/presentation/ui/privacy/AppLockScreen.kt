package dev.chungjungsoo.gptmobile.presentation.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.privacy.AppLockPolicy
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem
import dev.chungjungsoo.gptmobile.presentation.common.SettingsSection

@Composable
fun AppLockScreen(
    onBack: () -> Unit,
    viewModel: AppLockViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PrivacyScaffold(
        title = stringResource(R.string.app_lock_title),
        onBack = onBack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.app_lock_settings_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            if (!state.deviceLockAvailable) {
                Text(
                    text = stringResource(R.string.app_lock_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            SettingsSection(title = stringResource(R.string.app_lock_title)) {
                LockSwitch(
                    title = stringResource(R.string.app_lock_enable),
                    description = stringResource(R.string.app_lock_enable_description),
                    checked = state.enabled,
                    enabled = state.deviceLockAvailable || state.enabled,
                    onCheckedChange = viewModel::setEnabled
                )
                LockSwitch(
                    title = stringResource(R.string.app_lock_flag_secure),
                    description = stringResource(R.string.app_lock_flag_secure_description),
                    checked = state.flagSecure,
                    enabled = true,
                    onCheckedChange = viewModel::setFlagSecure
                )
            }
            SettingsSection(title = stringResource(R.string.app_lock_timeout)) {
                AppLockPolicy.timeoutChoicesSeconds.forEach { seconds ->
                    RadioItem(
                        value = seconds.toString(),
                        selected = state.timeoutSeconds == seconds,
                        title = timeoutLabel(seconds),
                        description = null,
                        enabled = state.enabled,
                        onSelected = { viewModel.setTimeoutSeconds(it.toInt()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LockSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun timeoutLabel(seconds: Int): String = when (seconds) {
    0 -> stringResource(R.string.app_lock_timeout_immediate)
    60 -> stringResource(R.string.app_lock_timeout_minute)
    300 -> stringResource(R.string.app_lock_timeout_five)
    else -> stringResource(R.string.app_lock_timeout_fifteen)
}
