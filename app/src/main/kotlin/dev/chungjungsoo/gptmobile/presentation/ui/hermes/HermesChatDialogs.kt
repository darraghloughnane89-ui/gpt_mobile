package dev.chungjungsoo.gptmobile.presentation.ui.hermes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.hermes.HermesAttachment
import dev.chungjungsoo.gptmobile.data.hermes.HermesHandoffKind
import dev.chungjungsoo.gptmobile.data.hermes.HermesHandoffRequest
import dev.chungjungsoo.gptmobile.data.hermes.HermesTranscript
import dev.chungjungsoo.gptmobile.data.hermes.HermesTranscriptTurn
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatAttachmentDraft

@Composable
fun HermesAskDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hermes_ask_title)) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.hermes_ask_title)) }
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onSend(text.trim()) }
            ) {
                Text(stringResource(R.string.hermes_ask_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun HermesChatSendDialog(
    onDismiss: () -> Unit,
    onTranscript: () -> Unit,
    onSummary: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hermes_send_chat)) },
        text = { Text(stringResource(R.string.hermes_settings_description, stringResource(R.string.brand_name))) },
        confirmButton = {
            TextButton(onClick = onTranscript) {
                Text(stringResource(R.string.hermes_send_transcript))
            }
        },
        dismissButton = {
            TextButton(onClick = onSummary) {
                Text(stringResource(R.string.hermes_send_summary))
            }
        }
    )
}

fun hermesMessageRequest(text: String, attachments: List<ChatAttachment>): HermesHandoffRequest = HermesHandoffRequest(
    kind = if (attachments.isEmpty()) HermesHandoffKind.MESSAGE else HermesHandoffKind.FILE,
    text = text,
    attachments = attachments.map { it.toHermesAttachment() }
)

fun hermesTaskRequest(text: String, drafts: List<ChatAttachmentDraft>): HermesHandoffRequest = HermesHandoffRequest(
    kind = HermesHandoffKind.TASK,
    text = text,
    attachments = drafts.map { draft ->
        HermesAttachment(
            displayName = draft.displayName,
            path = draft.attachment?.filePathForDisplay ?: draft.preparedFilePath ?: draft.sourceFilePath,
            mimeType = draft.mimeType.ifBlank { draft.attachment?.mimeType.orEmpty() }
        )
    }
)

fun hermesChatRequest(
    title: String,
    userMessages: List<MessageV2>,
    assistantMessages: List<List<MessageV2>>,
    summary: Boolean
): HermesHandoffRequest {
    val turns = chatTranscriptTurns(userMessages, assistantMessages)
    val text = if (summary) HermesTranscript.summary(title, turns) else HermesTranscript.full(title, turns)
    val attachments = (userMessages + assistantMessages.flatten()).flatMap { message ->
        message.attachments.map { it.toHermesAttachment() }
    }
    return HermesHandoffRequest(
        kind = if (summary) HermesHandoffKind.SUMMARY else HermesHandoffKind.TRANSCRIPT,
        text = text,
        attachments = attachments
    )
}

internal fun chatTranscriptTurns(
    userMessages: List<MessageV2>,
    assistantMessages: List<List<MessageV2>>
): List<HermesTranscriptTurn> = userMessages.flatMapIndexed { index, user ->
    val userTurn = HermesTranscriptTurn(
        speaker = "You",
        text = user.effectiveContent(),
        attachmentNames = user.attachments.map { it.resolvedDisplayName }
    )
    val replies = assistantMessages.getOrNull(index).orEmpty().map { assistant ->
        HermesTranscriptTurn(
            speaker = assistant.platformType?.takeIf { it.isNotBlank() } ?: "Assistant",
            text = assistant.effectiveContent(),
            attachmentNames = assistant.attachments.map { it.resolvedDisplayName }
        )
    }
    listOf(userTurn) + replies
}

private fun ChatAttachment.toHermesAttachment(): HermesAttachment = HermesAttachment(
    displayName = resolvedDisplayName,
    path = filePathForDisplay,
    mimeType = mimeType
)
