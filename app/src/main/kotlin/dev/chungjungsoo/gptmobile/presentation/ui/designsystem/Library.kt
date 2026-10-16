package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

data class SkillItem(
    val id: String,
    val title: String,
    val summary: String,
    val enabled: Boolean = false
)

data class ReviewItem(
    val id: String,
    val title: String,
    val source: String,
    val preview: String,
    val timeLabel: String,
    val unread: Boolean = true
)

@Composable
fun SkillCard(
    skill: SkillItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEnabledChange: ((Boolean) -> Unit)? = null
) {
    SurfaceCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(text = skill.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = skill.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onEnabledChange != null) {
                Switch(checked = skill.enabled, onCheckedChange = onEnabledChange)
            }
        }
    }
}

@Composable
fun ReviewInboxItem(
    review: ReviewItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SurfaceCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = review.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = review.timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = review.source,
                style = MaterialTheme.typography.labelMedium,
                color = if (review.unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = review.preview,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ReviewActionBar(
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onLater: (() -> Unit)? = null,
    approveLabel: String,
    dismissLabel: String,
    laterLabel: String? = null
) {
    val haptic = rememberHaptic()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = {
            haptic(HapticEvent.Reject)
            onDismiss()
        }) {
            Text(dismissLabel)
        }
        if (onLater != null && laterLabel != null) {
            TextButton(onClick = onLater) { Text(laterLabel) }
        }
        TextButton(onClick = {
            haptic(HapticEvent.Confirm)
            onApprove()
        }) {
            Text(approveLabel, color = MaterialTheme.colorScheme.primary)
        }
    }
}
