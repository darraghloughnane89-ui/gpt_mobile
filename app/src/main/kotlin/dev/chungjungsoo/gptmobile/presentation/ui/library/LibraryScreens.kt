package dev.chungjungsoo.gptmobile.presentation.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.common.Disclosure
import dev.chungjungsoo.gptmobile.presentation.common.NestEmptyState
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.MascotMood

@Composable
fun SkillsScreen(
    onBack: () -> Unit,
    tipViewModel: LibraryTipViewModel = hiltViewModel(),
    animated: Boolean = true
) {
    val dismissed by tipViewModel.dismissed.collectAsStateWithLifecycle()
    LibraryScaffold(title = stringResource(R.string.library_skills), onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            NestEmptyState(
                title = stringResource(R.string.shell_skills_title),
                body = stringResource(R.string.shell_skills_body),
                mood = MascotMood.Think,
                animated = animated,
                modifier = Modifier.weight(1f),
                tip = stringResource(R.string.tip_skills).takeIf { TipIds.SKILLS !in dismissed },
                onDismissTip = { tipViewModel.dismiss(TipIds.SKILLS) }
            )
            Disclosure(
                title = stringResource(R.string.onboarding_more),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_memory),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    tipViewModel: LibraryTipViewModel = hiltViewModel(),
    animated: Boolean = true
) {
    val dismissed by tipViewModel.dismissed.collectAsStateWithLifecycle()
    LibraryScaffold(title = stringResource(R.string.library_memory), onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            NestEmptyState(
                title = stringResource(R.string.shell_memory_title),
                body = stringResource(R.string.shell_memory_body),
                mood = MascotMood.Idle,
                animated = animated,
                modifier = Modifier.weight(1f),
                tip = stringResource(R.string.tip_memory).takeIf { TipIds.MEMORY !in dismissed },
                onDismissTip = { tipViewModel.dismiss(TipIds.MEMORY) }
            )
            Disclosure(
                title = stringResource(R.string.onboarding_more),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_memory),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            content()
        }
    }
}
