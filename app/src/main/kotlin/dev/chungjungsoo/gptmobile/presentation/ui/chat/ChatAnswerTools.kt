package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.chungjungsoo.gptmobile.R

@Composable
fun ChatAnswerTools(
    assistantContent: String,
    modelAnswers: List<ModelAnswer>
) {
    val artifacts = remember(assistantContent) { extractChatArtifacts(assistantContent) }
    val comparison = remember(modelAnswers) { compareModelAnswers(modelAnswers) }
    var showPreview by remember(assistantContent) { mutableStateOf(false) }
    var showCompare by remember(modelAnswers) { mutableStateOf(false) }
    if (artifacts.isEmpty() && comparison == null) return

    Row {
        if (artifacts.isNotEmpty()) {
            TextButton(onClick = { showPreview = true }) {
                Text(stringResource(R.string.preview_artifact))
            }
        }
        if (comparison != null) {
            TextButton(onClick = { showCompare = true }) {
                Text(stringResource(R.string.compare_models))
            }
        }
    }

    if (showPreview) {
        ArtifactPreviewDialog(
            artifacts = artifacts,
            onDismiss = { showPreview = false }
        )
    }
    if (showCompare && comparison != null) {
        ModelCompareDialog(
            comparison = comparison,
            onDismiss = { showCompare = false }
        )
    }
}
