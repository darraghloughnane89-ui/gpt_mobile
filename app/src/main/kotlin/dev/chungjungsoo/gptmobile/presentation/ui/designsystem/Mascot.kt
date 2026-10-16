package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import kotlin.math.sin

enum class MascotMood {
    Idle,
    Wave,
    Peek,
    Think
}

private const val MARK = 108f
private val Ember = Color(0xFFFF5C3A)
private val Amber = Color(0xFFFFB15A)
private val Clay = Color(0xFFE7C4B0)

/**
 * Geometric bird drawn in Compose so the wing, eye, and perch can move
 * without a Lottie runtime. The launcher mark stays a separate static asset.
 */
@Composable
fun Mascot(
    mood: MascotMood,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    size: Dp = 96.dp
) {
    val phase = motionPhase(animated = animated, durationMillis = if (mood == MascotMood.Think) 1800 else 1200)
    val bob = if (animated) sin(phase * 6.2832f) else 0f
    val blink = if (!animated) {
        1f
    } else {
        val window = (phase * 2f) % 1f
        if (window > 0.92f) 0.2f else 1f
    }
    val wing = when {
        !animated -> -8f
        mood == MascotMood.Wave -> bob * 26f
        else -> bob * 6f
    }
    val lean = when (mood) {
        MascotMood.Peek -> -8f
        MascotMood.Think -> bob * 4f
        MascotMood.Wave -> bob * 6f
        MascotMood.Idle -> bob * 2f
    }
    val hop = when (mood) {
        MascotMood.Wave -> bob * 5f
        MascotMood.Think -> bob * 2f
        else -> bob * 3f
    }
    val description = stringResource(R.string.mascot_content_description)
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
    ) {
        val unit = this.size.minDimension / MARK
        scale(unit, unit, pivot = Offset.Zero) {
            translate(left = hop * 0.4f, top = hop) {
                rotate(degrees = lean, pivot = Offset(70f, 70f)) {
                    tail()
                    body()
                    breast()
                    rotate(degrees = wing, pivot = Offset(58f, 70f)) {
                        wing()
                    }
                    head()
                    beak()
                    eye(open = blink)
                }
            }
        }
    }
}

private fun DrawScope.tail() {
    drawPath(
        color = Clay,
        path = Path().apply {
            moveTo(34f, 64f)
            cubicTo(22f, 54f, 22f, 40f, 36f, 36f)
            cubicTo(32f, 50f, 40f, 60f, 50f, 64f)
            cubicTo(42f, 62f, 36f, 64f, 34f, 64f)
            close()
        }
    )
}

private fun DrawScope.body() {
    oval(60f, 72f, 20f, 16f, Color(0xFFFFF6F1))
}

private fun DrawScope.head() {
    oval(76f, 52f, 14f, 14f, Color(0xFFFFF6F1))
}

private fun DrawScope.breast() {
    oval(68f, 74f, 12f, 10f, Ember)
}

private fun DrawScope.wing() {
    oval(61f, 69f, 10f, 7f, Clay)
}

private fun DrawScope.beak() {
    drawPath(
        color = Amber,
        path = Path().apply {
            moveTo(86f, 48f)
            lineTo(104f, 56f)
            lineTo(86f, 62f)
            close()
        }
    )
}

private fun DrawScope.eye(open: Float) {
    oval(80.4f, 50f, 2.4f, 2.4f * open, Color(0xFF1A0C09))
}

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) {
    drawOval(
        color = color,
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2f, ry * 2f)
    )
}

@Composable
internal fun motionPhase(animated: Boolean, durationMillis: Int): Float {
    if (!animated) return 0f
    val transition = rememberInfiniteTransition(label = "mascot")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "phase"
    )
    return phase
}
