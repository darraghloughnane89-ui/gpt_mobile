package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val phase = if (animated) {
        val transition = rememberInfiniteTransition(label = "typing")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "typing phase"
        )
        value
    } else {
        0.35f
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val wave = if (animated) {
                val shifted = (phase + index * 0.22f) * 6.28f
                0.45f + 0.55f * ((sin(shifted) + 1f) / 2f)
            } else {
                0.7f
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer { alpha = wave }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun StreamingText(
    text: String,
    shimmer: Boolean,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val shift = if (shimmer && animated) {
        val transition = rememberInfiniteTransition(label = "shimmer text")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmer shift"
        )
        value
    } else {
        0.5f
    }
    val base = MaterialTheme.colorScheme.onSurface
    val flash = MaterialTheme.colorScheme.secondary
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge.copy(
            brush = if (shimmer) {
                Brush.linearGradient(
                    colors = listOf(base, flash, base),
                    start = androidx.compose.ui.geometry.Offset(shift * 400f, 0f),
                    end = androidx.compose.ui.geometry.Offset(shift * 400f + 180f, 40f)
                )
            } else {
                null
            }
        )
    )
}
