package dev.chungjungsoo.gptmobile.presentation.fastentry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.fastentry.FastEntryPlanner

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAskSheet(
    title: String,
    showJumps: Boolean,
    viewModel: QuickAskViewModel,
    onDismiss: () -> Unit,
    onContinue: (chatId: Int, platforms: List<String>) -> Unit,
    onNewChat: () -> Unit,
    onVoice: () -> Unit,
    onReplace: (String) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sending = state.phase == FastEntryPlanner.Phase.SENDING
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .imePadding(),
        verticalArrangement = Arrangement.Bottom
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (showJumps) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onNewChat, enabled = !sending) {
                        Text(stringResource(R.string.shortcut_new_chat))
                    }
                    TextButton(onClick = onVoice, enabled = !sending) {
                        Text(stringResource(R.string.shortcut_voice))
                    }
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.draft,
                onValueChange = viewModel::updateDraft,
                enabled = !sending,
                minLines = 2,
                maxLines = 6,
                label = { Text(stringResource(R.string.quick_ask_hint)) }
            )
            when {
                sending && state.answer.isBlank() -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.quick_ask_thinking),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                state.answer.isNotBlank() -> {
                    Text(
                        text = state.answer,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            state.error?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
                if (state.chatId > 0) {
                    TextButton(onClick = { onContinue(state.chatId, state.platforms) }) {
                        Text(stringResource(R.string.quick_ask_continue))
                    }
                }
                if (state.canReplaceSelection && state.phase == FastEntryPlanner.Phase.ANSWERED && state.answer.isNotBlank()) {
                    TextButton(onClick = { onReplace(state.answer) }) {
                        Text(stringResource(R.string.quick_ask_replace))
                    }
                }
                Button(
                    onClick = viewModel::send,
                    enabled = !sending && state.draft.isNotBlank()
                ) {
                    Text(stringResource(R.string.quick_ask_send))
                }
            }
        }
    }
}
