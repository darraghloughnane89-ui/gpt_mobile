package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureShellScreen(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
    onBack: () -> Unit
) {
    AppNavScaffold(current = destination, onDestination = onDestination) { _, openCommand ->
        Scaffold(
            topBar = {
                BlurTopBar(
                    title = destination.title(),
                    onNavigation = onBack,
                    navigationDescription = stringResource(R.string.go_back),
                    onCommand = openCommand
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (destination) {
                    AppDestination.Voice -> VoiceShell()
                    AppDestination.Obsidian -> NoteShell(
                        title = destination.emptyTitle(),
                        body = destination.shellBody(),
                        detailTitle = stringResource(R.string.shell_obsidian_detail_title),
                        detail = stringResource(R.string.shell_obsidian_detail)
                    )
                    AppDestination.Usage -> NoteShell(
                        title = destination.emptyTitle(),
                        body = destination.shellBody(),
                        detailTitle = stringResource(R.string.shell_usage_detail_title),
                        detail = stringResource(R.string.shell_usage_detail)
                    )
                    else -> EmptyState(
                        title = destination.emptyTitle(),
                        body = destination.shellBody(),
                        mood = if (destination == AppDestination.Review) MascotMood.Peek else MascotMood.Wave
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceShell() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        VoiceOrb(
            amplitude = 0.2f,
            state = VoiceOrbState.Idle,
            modifier = Modifier.size(220.dp),
            contentDescription = stringResource(R.string.nav_voice)
        )
        Text(
            text = stringResource(R.string.shell_voice_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Space.xl)
        )
        Disclosure(title = stringResource(R.string.shell_voice_detail_title)) {
            Text(
                text = stringResource(R.string.shell_voice_detail),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun NoteShell(
    title: String,
    body: String,
    detailTitle: String,
    detail: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(title = title, body = body, mood = MascotMood.Think)
        Disclosure(title = detailTitle) {
            Text(text = detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
