package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

@Composable
fun ModelCompareDialog(
    comparison: ModelComparison,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compare_models_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.compare_overlap, comparison.overlapPercent))
                comparison.answers.forEach { answer ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = answer.label, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.compare_words, answer.wordCount))
                    if (answer.uniqueSentences.isEmpty()) {
                        Text(stringResource(R.string.compare_no_unique))
                    } else {
                        Text(stringResource(R.string.compare_unique))
                        answer.uniqueSentences.forEach { sentence ->
                            Text(text = "• $sentence")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
