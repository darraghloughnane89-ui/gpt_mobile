package dev.chungjungsoo.gptmobile.presentation.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.voice.MiMoSpeechModels
import dev.chungjungsoo.gptmobile.data.voice.VoiceBackendStatus
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    onNavigationClick: () -> Unit,
    viewModel: VoiceSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sample = stringResource(R.string.voice_sample)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.preview(sample)
    }
    var keyDraft by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.voice_use_mimo),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = backendLabel(state.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.useMiMo,
                    onCheckedChange = viewModel::setUseMiMo
                )
            }
            Text(
                text = stringResource(R.string.voice_fallback_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = keyDraft,
                onValueChange = { keyDraft = it },
                label = { Text(stringResource(R.string.voice_api_key)) },
                placeholder = { Text(stringResource(R.string.voice_api_key_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                supportingText = {
                    if (state.hasKey) {
                        val preview = state.keyPreview
                        Text(
                            if (preview.isEmpty()) {
                                stringResource(R.string.voice_key_saved)
                            } else {
                                stringResource(R.string.voice_key_saved) + " ••••" + preview
                            }
                        )
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.saveKey(keyDraft)
                        keyDraft = ""
                    },
                    enabled = keyDraft.isNotBlank()
                ) {
                    Text(stringResource(R.string.voice_save_key))
                }
                if (state.hasKey) {
                    TextButton(onClick = viewModel::clearKey) {
                        Text(stringResource(R.string.voice_clear_key))
                    }
                }
            }
            Text(
                text = stringResource(R.string.voice_builtin_voice),
                style = MaterialTheme.typography.titleMedium
            )
            MiMoSpeechModels.builtinVoices.forEach { voice ->
                RadioItem(
                    value = voice.id,
                    selected = state.voiceId == voice.id,
                    title = voice.label,
                    description = voice.language,
                    enabled = state.useMiMo,
                    onSelected = viewModel::setVoice
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            viewModel.preview(sample)
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = !state.playing
                ) {
                    Text(stringResource(R.string.voice_play_sample))
                }
                OutlinedButton(
                    onClick = viewModel::stopPreview,
                    enabled = state.playing
                ) {
                    Text(stringResource(R.string.voice_stop_sample))
                }
            }
            state.error?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun backendLabel(status: VoiceBackendStatus): String = when (status) {
    VoiceBackendStatus.MIMO -> stringResource(R.string.voice_backend_mimo)
    VoiceBackendStatus.DEVICE_MISSING_KEY -> stringResource(R.string.voice_backend_missing_key)
    VoiceBackendStatus.DEVICE_BY_CHOICE -> stringResource(R.string.voice_backend_device)
}
