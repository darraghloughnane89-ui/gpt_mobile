package dev.chungjungsoo.gptmobile.presentation.ui.hermes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesSettingsScreen(
    state: HermesSettingsUiState,
    onEndpointChange: (String) -> Unit,
    onProfileChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onChatModelChange: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onSave: () -> Unit,
    onOpenThread: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hermes_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.hermes_settings_description, stringResource(R.string.brand_name)),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.hermes_reachability),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.endpoint,
                onValueChange = onEndpointChange,
                label = { Text(stringResource(R.string.hermes_endpoint)) },
                supportingText = {
                    Text(state.endpointError ?: stringResource(R.string.hermes_endpoint_supporting))
                },
                isError = state.endpointError != null,
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.tokenDraft,
                onValueChange = onTokenChange,
                label = { Text(stringResource(R.string.hermes_token)) },
                supportingText = {
                    Text(
                        if (state.tokenSet) {
                            stringResource(R.string.hermes_token_saved)
                        } else {
                            stringResource(R.string.hermes_token_supporting)
                        }
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.profile,
                onValueChange = onProfileChange,
                label = { Text(stringResource(R.string.hermes_profile)) },
                supportingText = {
                    Text(state.profileError ?: stringResource(R.string.hermes_profile_supporting))
                },
                isError = state.profileError != null,
                singleLine = true
            )
            Button(
                onClick = onTestConnection,
                enabled = !state.testing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (state.testing) {
                        stringResource(R.string.hermes_testing)
                    } else {
                        stringResource(R.string.hermes_test_connection)
                    }
                )
            }
            state.testResult?.let { result ->
                Text(text = result, style = MaterialTheme.typography.bodyMedium)
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.hermes_chat_model)) },
                supportingContent = { Text(stringResource(R.string.hermes_chat_model_description)) },
                trailingContent = {
                    Switch(
                        checked = state.chatModelEnabled,
                        onCheckedChange = onChatModelChange
                    )
                }
            )
            Button(
                onClick = onSave,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.hermes_save))
            }
            TextButton(onClick = onOpenThread, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.hermes_open_thread))
            }
        }
    }
}
