package dev.chungjungsoo.gptmobile.presentation.ui.hermes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.hermes.HermesDeliveryStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesThreadScreen(
    state: HermesThreadUiState,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
    onNewThread: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.title.ifBlank { stringResource(R.string.hermes_thread_title) })
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onNewThread) {
                        Text(stringResource(R.string.hermes_new_thread))
                    }
                }
            )
        }
    ) { padding ->
        if (state.turns.isEmpty()) {
            Text(
                text = stringResource(R.string.hermes_empty_thread),
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.turns, key = { it.id }) { turn ->
                    HermesTurnCard(turn = turn, onRetry = onRetry)
                }
            }
        }
    }
}

@Composable
private fun HermesTurnCard(
    turn: HermesTurnUi,
    onRetry: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (turn.isUser) {
                    stringResource(R.string.hermes_you)
                } else {
                    stringResource(R.string.hermes_agent_name)
                },
                style = MaterialTheme.typography.labelLarge
            )
            turn.status?.let { status ->
                Text(
                    text = hermesStatusText(status),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (turn.text.isNotBlank()) {
                Text(text = turn.text, style = MaterialTheme.typography.bodyMedium)
            }
            turn.toolNotes.forEach { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            turn.error?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (turn.status == HermesDeliveryStatus.FAILED || turn.status == HermesDeliveryStatus.INTERRUPTED) {
                TextButton(onClick = { onRetry(turn.id) }) {
                    Text(stringResource(R.string.hermes_retry))
                }
            }
        }
    }
}

@Composable
fun hermesStatusText(status: HermesDeliveryStatus): String = when (status) {
    HermesDeliveryStatus.QUEUED -> stringResource(R.string.hermes_status_queued)
    HermesDeliveryStatus.SENDING -> stringResource(R.string.hermes_status_sending)
    HermesDeliveryStatus.STREAMING -> stringResource(R.string.hermes_status_streaming)
    HermesDeliveryStatus.DELIVERED -> stringResource(R.string.hermes_status_delivered)
    HermesDeliveryStatus.FAILED -> stringResource(R.string.hermes_status_failed)
    HermesDeliveryStatus.INTERRUPTED -> stringResource(R.string.hermes_status_interrupted)
}
