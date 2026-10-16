package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.diagnostics.ProviderFailureKind

@Composable
fun ProviderFailureCard(
    kind: ProviderFailureKind,
    canRetry: Boolean,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = providerFailureTitle(kind),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = providerFailureBody(kind),
                style = MaterialTheme.typography.bodyMedium
            )
            if (canRetry) {
                TextButton(onClick = onRetryClick) {
                    Text(text = stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
fun providerFailureTitle(kind: ProviderFailureKind): String = when (kind) {
    ProviderFailureKind.RATE_LIMIT -> stringResource(R.string.chat_error_rate_limit_title)
    ProviderFailureKind.AUTH -> stringResource(R.string.chat_error_auth_title)
    ProviderFailureKind.TIMEOUT -> stringResource(R.string.chat_error_timeout_title)
    ProviderFailureKind.NETWORK -> stringResource(R.string.chat_error_network_title)
    ProviderFailureKind.GENERIC -> stringResource(R.string.diagnostics_provider_error)
}

@Composable
fun providerFailureBody(kind: ProviderFailureKind): String = when (kind) {
    ProviderFailureKind.RATE_LIMIT -> stringResource(R.string.chat_error_rate_limit_body)
    ProviderFailureKind.AUTH -> stringResource(R.string.chat_error_auth_body)
    ProviderFailureKind.TIMEOUT -> stringResource(R.string.chat_error_timeout_body)
    ProviderFailureKind.NETWORK -> stringResource(R.string.chat_error_network_body)
    ProviderFailureKind.GENERIC -> stringResource(R.string.diagnostics_provider_error)
}
