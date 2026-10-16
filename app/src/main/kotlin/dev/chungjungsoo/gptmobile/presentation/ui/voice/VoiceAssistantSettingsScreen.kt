package dev.chungjungsoo.gptmobile.presentation.ui.voice

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.voice.VoiceEngine
import dev.chungjungsoo.gptmobile.presentation.common.RadioItem
import dev.chungjungsoo.gptmobile.presentation.common.SettingsSection
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantSettingsScreen(
    onNavigationClick: () -> Unit,
    viewModel: VoiceSettingsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val openAiAvailable by viewModel.openAiAvailable.collectAsStateWithLifecycle()
    val assistantAvailable by viewModel.assistantAvailable.collectAsStateWithLifecycle()
    val assistantHeld by viewModel.assistantHeld.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refresh()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Text(
                        text = stringResource(R.string.voice_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(4.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick, modifier = Modifier.padding(4.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection(title = stringResource(R.string.voice_section_engines)) {
                Text(
                    text = stringResource(R.string.voice_stt_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                RadioItem(
                    value = VoiceEngine.DEVICE.name,
                    selected = settings.speechRecognition == VoiceEngine.DEVICE,
                    title = stringResource(R.string.voice_engine_device),
                    description = stringResource(R.string.voice_engine_device_stt_description),
                    onSelected = { viewModel.setSpeechRecognition(VoiceEngine.DEVICE) }
                )
                RadioItem(
                    value = VoiceEngine.OPENAI.name,
                    selected = settings.speechRecognition == VoiceEngine.OPENAI,
                    title = stringResource(R.string.voice_engine_openai),
                    description = stringResource(R.string.voice_engine_openai_stt_description),
                    enabled = openAiAvailable,
                    onSelected = { viewModel.setSpeechRecognition(VoiceEngine.OPENAI) }
                )
                Text(
                    text = stringResource(R.string.voice_tts_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                RadioItem(
                    value = VoiceEngine.DEVICE.name,
                    selected = settings.speechSynthesis == VoiceEngine.DEVICE,
                    title = stringResource(R.string.voice_engine_device),
                    description = stringResource(R.string.voice_engine_device_tts_description),
                    onSelected = { viewModel.setSpeechSynthesis(VoiceEngine.DEVICE) }
                )
                RadioItem(
                    value = VoiceEngine.OPENAI.name,
                    selected = settings.speechSynthesis == VoiceEngine.OPENAI,
                    title = stringResource(R.string.voice_engine_openai),
                    description = stringResource(R.string.voice_engine_openai_tts_description),
                    enabled = openAiAvailable,
                    onSelected = { viewModel.setSpeechSynthesis(VoiceEngine.OPENAI) }
                )
                if (!openAiAvailable) {
                    Text(
                        text = stringResource(R.string.voice_openai_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
            SettingsSection(title = stringResource(R.string.voice_section_assistant)) {
                Text(
                    text = when {
                        assistantHeld -> stringResource(R.string.voice_assistant_status_held)
                        assistantAvailable -> stringResource(R.string.voice_assistant_status_available)
                        else -> stringResource(R.string.voice_assistant_status_unavailable)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Text(
                    text = stringResource(R.string.voice_assistant_step_role),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                Text(
                    text = stringResource(R.string.voice_assistant_step_gesture),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                Text(
                    text = stringResource(R.string.voice_assistant_step_use),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                Text(
                    text = stringResource(R.string.voice_bluetooth_headset),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                if (assistantAvailable && !assistantHeld) {
                    Button(
                        onClick = {
                            val intent = viewModel.assistantRequestIntent() ?: return@Button
                            if (context is Activity) {
                                roleLauncher.launch(intent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.voice_set_assistant))
                    }
                }
            }
        }
    }
}
