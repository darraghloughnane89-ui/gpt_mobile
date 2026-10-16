package dev.chungjungsoo.gptmobile.presentation.ui.onboarding

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.onboarding.MiMoSuggestion
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveFailure
import dev.chungjungsoo.gptmobile.presentation.common.Disclosure
import dev.chungjungsoo.gptmobile.presentation.common.PrimaryLongButton
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.Mascot
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.MascotMood

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    animated: Boolean = true,
    onFinished: (OnboardingDestination) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.importZip(uri)
    }
    val openFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) viewModel.importFolder(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.destination.collect(onFinished)
    }
    BackHandler(enabled = state.step != OnboardingStep.Welcome && !state.busy) {
        viewModel.back()
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.onboarding_step, state.stepNumber, state.stepCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = viewModel::skip,
                    enabled = !state.busy
                ) {
                    Text(
                        stringResource(
                            if (state.step == OnboardingStep.Welcome) {
                                R.string.onboarding_skip_setup
                            } else {
                                R.string.onboarding_skip
                            }
                        )
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state.step) {
                    OnboardingStep.Welcome -> WelcomeStep(animated)
                    OnboardingStep.Provider -> ProviderStep(state, viewModel, animated)
                    OnboardingStep.Import -> ImportStep(
                        state = state,
                        animated = animated,
                        onPickZip = {
                            openZip.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        },
                        onPickFolder = { openFolder.launch(null) }
                    )
                    OnboardingStep.Voice -> VoiceStep(state, viewModel, animated)
                    OnboardingStep.Explore -> ExploreStep(viewModel, animated)
                }
            }
            PrimaryLongButton(
                modifier = Modifier.padding(bottom = 20.dp),
                enabled = when (state.step) {
                    OnboardingStep.Provider -> state.providerReady && !state.busy
                    else -> !state.busy
                },
                onClick = viewModel::next,
                text = when (state.step) {
                    OnboardingStep.Welcome -> stringResource(R.string.get_started)
                    OnboardingStep.Explore -> stringResource(R.string.done)
                    else -> stringResource(R.string.next)
                }
            )
        }
    }
}

@Composable
private fun WelcomeStep(animated: Boolean) {
    val brand = stringResource(R.string.app_name)
    Spacer(modifier = Modifier.height(12.dp))
    Mascot(mood = MascotMood.Wave, size = 132.dp, animated = animated)
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = stringResource(R.string.onboarding_welcome, brand),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_nest),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Disclosure(title = stringResource(R.string.onboarding_more)) {
        Text(stringResource(R.string.onboarding_voice), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_first_bot), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_memory), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ProviderStep(state: OnboardingUiState, viewModel: OnboardingViewModel, animated: Boolean) {
    Mascot(mood = MascotMood.Peek, animated = animated)
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_provider_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_provider_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = state.providerName.ifBlank { MiMoSuggestion.NAME }, style = MaterialTheme.typography.titleMedium)
            Text(
                text = state.model.ifBlank { MiMoSuggestion.MODEL },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = state.apiUrl.ifBlank { MiMoSuggestion.API_URL },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        value = state.apiKey,
        onValueChange = viewModel::updateApiKey,
        label = { Text(stringResource(R.string.api_key)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        enabled = !state.isSaving
    )
    if (state.isSaving) {
        CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
    }
    if (state.saveFailed) {
        Text(
            text = stringResource(R.string.onboarding_key_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    Disclosure(title = stringResource(R.string.onboarding_other_endpoint)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.providerName,
            onValueChange = viewModel::updateProviderName,
            label = { Text(stringResource(R.string.onboarding_provider_name)) },
            singleLine = true
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            value = state.apiUrl,
            onValueChange = viewModel::updateApiUrl,
            label = { Text(stringResource(R.string.onboarding_base_url)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            value = state.model,
            onValueChange = viewModel::updateModel,
            label = { Text(stringResource(R.string.onboarding_model)) },
            singleLine = true
        )
        if (!state.usingMiMo) {
            TextButton(onClick = viewModel::resetToMiMo) {
                Text(stringResource(R.string.onboarding_use_mimo))
            }
        }
    }
}

@Composable
private fun ImportStep(
    state: OnboardingUiState,
    animated: Boolean,
    onPickZip: () -> Unit,
    onPickFolder: () -> Unit
) {
    Mascot(mood = MascotMood.Idle, animated = animated)
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_import_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_import_body),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    TextButton(onClick = onPickZip, enabled = !state.isImporting) {
        Text(stringResource(R.string.project_import_zip))
    }
    TextButton(onClick = onPickFolder, enabled = !state.isImporting) {
        Text(stringResource(R.string.project_import_folder))
    }
    if (state.isImporting) {
        CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
    }
    state.importedProjects?.let { projects ->
        Text(
            text = stringResource(R.string.project_import_done, projects, state.importedChats),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
    }
    state.importFailure?.let { failure ->
        Text(
            text = stringResource(failure.messageRes()),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
    }
    Disclosure(title = stringResource(R.string.onboarding_import_details)) {
        Text(
            text = stringResource(R.string.onboarding_import_formats),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VoiceStep(state: OnboardingUiState, viewModel: OnboardingViewModel, animated: Boolean) {
    Mascot(mood = MascotMood.Think, animated = animated)
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_voice),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(modifier = Modifier.height(16.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = stringResource(R.string.onboarding_voice_switch), style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = state.voiceEnabled,
                onCheckedChange = viewModel::setVoiceEnabled
            )
        }
    }
    Disclosure(title = stringResource(R.string.onboarding_voice_details)) {
        Text(
            text = stringResource(R.string.onboarding_voice_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExploreStep(viewModel: OnboardingViewModel, animated: Boolean) {
    Mascot(mood = MascotMood.Wave, animated = animated)
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.onboarding_explore_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.onboarding_ready),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = viewModel::openBots) {
        Text(stringResource(R.string.onboarding_open_bots))
    }
    Text(
        text = stringResource(R.string.onboarding_first_bot),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    TextButton(onClick = viewModel::openProjects) {
        Text(stringResource(R.string.onboarding_open_projects))
    }
    Disclosure(title = stringResource(R.string.onboarding_explore_details)) {
        Text(
            text = stringResource(R.string.shell_projects_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun ProjectArchiveFailure.messageRes(): Int = when (this) {
    ProjectArchiveFailure.EMPTY -> R.string.project_import_empty
    ProjectArchiveFailure.TOO_LARGE -> R.string.project_import_too_large
    ProjectArchiveFailure.UNREADABLE -> R.string.project_import_unreadable
    ProjectArchiveFailure.UNRECOGNIZED -> R.string.project_import_unrecognized
}
