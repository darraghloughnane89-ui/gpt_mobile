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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    animated: Boolean = true
) {
    val shift = shimmerShift(animated)
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(shift * 600f, 0f),
                    end = Offset(shift * 600f + 240f, 80f)
                )
            )
    )
}

@Composable
fun ChatListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 4,
    animated: Boolean = true
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        repeat(rows) { index ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                Skeleton(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    animated = animated
                )
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Skeleton(
                        modifier = Modifier
                            .width(if (index % 2 == 0) 180.dp else 140.dp)
                            .height(14.dp),
                        animated = animated
                    )
                    Skeleton(
                        modifier = Modifier
                            .width(96.dp)
                            .height(10.dp),
                        animated = animated
                    )
                }
            }
        }
    }
}

@Composable
private fun shimmerShift(animated: Boolean): Float {
    if (!animated) return 0.4f
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "skeleton shift"
    )
    return shift
}

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    Skeleton(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .padding(vertical = 0.dp),
        animated = animated
    )
}
