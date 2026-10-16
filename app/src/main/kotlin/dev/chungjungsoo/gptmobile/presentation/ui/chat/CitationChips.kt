package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.search.WebCitation

@Composable
fun CitationChips(
    citations: List<WebCitation>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        citations.forEach { citation ->
            val label = citation.title.ifBlank { citation.url }
            SuggestionChip(
                onClick = { openCitation(context, citation.url) },
                label = {
                    Text(
                        text = stringResource(R.string.citation_chip, citation.number, label),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            )
        }
    }
}

private fun openCitation(context: Context, url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
