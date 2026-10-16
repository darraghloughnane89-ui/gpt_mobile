package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.fetch.AttachedLink
import dev.chungjungsoo.gptmobile.data.fetch.LinkChip
import dev.chungjungsoo.gptmobile.data.fetch.LinkKind

@Composable
fun ReadLinkOffer(
    onReadLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    SuggestionChip(
        modifier = modifier,
        onClick = onReadLink,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null
            )
        },
        label = { Text(text = stringResource(R.string.read_link)) }
    )
}

@Composable
fun ComposerLinkChips(
    chips: List<LinkChip>,
    onSummarise: (String) -> Unit,
    onAsk: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        chips.forEach { chip ->
            ComposerLinkChip(
                chip = chip,
                onSummarise = { onSummarise(chip.url) },
                onAsk = { onAsk(chip.url) },
                onRemove = { onRemove(chip.url) },
                onRetry = { onRetry(chip.url) }
            )
        }
    }
}

@Composable
private fun ComposerLinkChip(
    chip: LinkChip,
    onSummarise: () -> Unit,
    onAsk: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (chip.status == LinkChip.Status.READING) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp),
                    strokeWidth = 2.dp
                )
            }
            Text(
                modifier = Modifier.weight(1f),
                text = chip.title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(if (chip.kind == LinkKind.YOUTUBE) R.string.read_link_captions else R.string.read_link_untrusted),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (chip.status == LinkChip.Status.READY) {
                TextButton(onClick = onSummarise) {
                    Text(text = stringResource(R.string.read_link_summarise))
                }
                TextButton(onClick = onAsk) {
                    Text(text = stringResource(R.string.read_link_ask))
                }
            }
            if (chip.status == LinkChip.Status.FAILED) {
                TextButton(onClick = onRetry) {
                    Text(text = stringResource(R.string.read_link))
                }
            }
            TextButton(onClick = onRemove) {
                Text(text = stringResource(R.string.remove))
            }
        }
    }
}

@Composable
fun SentLinkChips(
    links: List<AttachedLink>,
    modifier: Modifier = Modifier
) {
    if (links.isEmpty()) return
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        links.forEach { link ->
            SentLinkChip(link)
        }
    }
}

@Composable
private fun SentLinkChip(link: AttachedLink) {
    var expanded by rememberSaveable(link.source, link.title) { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.End) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SuggestionChip(
                onClick = { expanded = !expanded },
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Link,
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        text = link.title.ifBlank { link.source },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
        Text(
            text = stringResource(if (link.kind == LinkKind.YOUTUBE) R.string.read_link_captions else R.string.read_link_untrusted),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (expanded) {
            Text(
                modifier = Modifier
                    .heightIn(max = 160.dp)
                    .verticalScroll(rememberScrollState()),
                text = link.text,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
