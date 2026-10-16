package dev.chungjungsoo.gptmobile.presentation.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.security.SecretType

@Composable
fun KeyManagerScreen(
    onBack: () -> Unit,
    viewModel: KeyManagerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalView.current.context as? FragmentActivity
    PrivacyScaffold(
        title = stringResource(R.string.key_manager_title),
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
                text = stringResource(R.string.key_manager_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            if (!state.unlocked) {
                TextButton(
                    onClick = {
                        val host = activity ?: return@TextButton
                        AppLockAuthenticator.authenticate(
                            activity = host,
                            title = host.getString(R.string.vault_unlock_title),
                            subtitle = host.getString(R.string.vault_unlock_subtitle),
                            onResult = { success -> if (success) viewModel.onUnlocked() }
                        )
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(stringResource(R.string.vault_unlock_action))
                }
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (state.rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.key_manager_empty),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            state.rows.forEach { row ->
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(text = row.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${row.type.name} · ${row.masked}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        TextButton(onClick = { viewModel.beginRotate(row) }) {
                            Text(stringResource(R.string.key_manager_rotate))
                        }
                        TextButton(onClick = { viewModel.test(row) }) {
                            Text(stringResource(R.string.key_manager_test))
                        }
                        TextButton(onClick = { viewModel.delete(row) }) {
                            Text(stringResource(R.string.key_manager_delete))
                        }
                    }
                }
            }
            TextButton(
                onClick = viewModel::beginAdd,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(stringResource(R.string.key_manager_add))
            }
        }
    }
    state.editor?.let { editor ->
        KeyEditorDialog(
            editor = editor,
            onChange = viewModel::updateEditor,
            onDismiss = viewModel::dismissEditor,
            onSave = viewModel::save
        )
    }
}

@Composable
private fun KeyEditorDialog(
    editor: KeyEditorState,
    onChange: (KeyEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editor.existingId == null) R.string.key_manager_add else R.string.key_manager_rotate
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editor.label,
                    onValueChange = { onChange(editor.copy(label = it)) },
                    label = { Text(stringResource(R.string.key_manager_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = editor.endpoint,
                    onValueChange = { onChange(editor.copy(endpoint = it)) },
                    label = { Text(stringResource(R.string.api_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = editor.secret,
                    onValueChange = { onChange(editor.copy(secret = it)) },
                    label = { Text(stringResource(R.string.key_manager_secret)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (editor.platformId == null && editor.existingId == null) {
                    SecretType.entries.forEach { type ->
                        TextButton(onClick = { onChange(editor.copy(type = type)) }) {
                            Text(if (editor.type == type) "● ${type.name}" else type.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.key_manager_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.key_manager_cancel)) }
        }
    )
}
