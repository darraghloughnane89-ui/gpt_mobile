package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class ModelOption(
    val id: String,
    val name: String,
    val detail: String? = null,
    val accent: Color? = null
)

@Composable
fun ModelChip(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val haptic = rememberHaptic()
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.98f,
        animationSpec = snapSpring(),
        label = "chip scale"
    )
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = fadeSpring(),
        label = "chip color"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = fadeSpring(),
        label = "chip content"
    )
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.extraLarge)
            .background(container)
            .clickable {
                haptic(HapticEvent.Tick)
                onClick()
            }
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Text(
            text = name,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSwitcherSheet(
    models: List<ModelOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = Space.sm)
            )
            models.forEach { model ->
                ModelChip(
                    name = model.name,
                    selected = model.id == selectedId,
                    accent = model.accent ?: MaterialTheme.colorScheme.primary,
                    onClick = { onSelect(model.id) }
                )
                model.detail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.lg, bottom = Space.sm)
                    )
                }
            }
        }
    }
}
