package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

@Composable
fun ChatQuickActionRow(
    enabled: Boolean,
    onAction: (ChatQuickAction) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChatQuickAction.entries.forEach { action ->
            SuggestionChip(
                onClick = { onAction(action) },
                label = { Text(stringResource(quickActionLabel(action))) }
            )
        }
    }
}

private fun quickActionLabel(action: ChatQuickAction): Int = when (action) {
    ChatQuickAction.Shorter -> R.string.quick_shorter
    ChatQuickAction.Explain -> R.string.quick_explain
    ChatQuickAction.Continue -> R.string.quick_continue
    ChatQuickAction.Proofread -> R.string.quick_proofread
    ChatQuickAction.Bullets -> R.string.quick_bullets
    ChatQuickAction.Irish -> R.string.quick_irish
}
