package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlin.math.cos
import kotlin.math.sin

enum class VoiceOrbState {
    Idle,
    Listening,
    Speaking,
    Thinking
}

@Composable
fun VoiceOrb(
    amplitude: Float,
    state: VoiceOrbState,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    contentDescription: String = "",
    onClick: (() -> Unit)? = null
) {
    val level = amplitude.coerceIn(0f, 1f)
    val phase = motionPhase(animated = animated, durationMillis = if (state == VoiceOrbState.Thinking) 2200 else 2800)
    val turn = phase * 6.2832f
    val clickable = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(clickable)
            .semantics {
                if (contentDescription.isNotEmpty()) {
                    this.contentDescription = contentDescription
                }
                if (onClick != null) role = Role.Button
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val breathe = if (animated) 0.06f * sin(turn) else 0f
            val energy = when (state) {
                VoiceOrbState.Idle -> 0.08f + breathe
                VoiceOrbState.Listening -> 0.12f + level * 0.28f
                VoiceOrbState.Speaking -> 0.18f + level * 0.34f + breathe
                VoiceOrbState.Thinking -> 0.1f + breathe
            }
            val core = radius * (0.46f + energy)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x66FFB15A), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius * 0.96f,
                center = center
            )
            repeat(3) { index ->
                val wobble = if (animated) sin(turn * (1.4f + index * 0.35f) + index) else 0f
                val ring = core * (1.15f + index * 0.16f + wobble * 0.04f * (1f + level))
                drawCircle(
                    color = Ember.copy(alpha = 0.18f - index * 0.04f),
                    radius = ring,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f + level * 3f)
                )
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFD7A8), Ember, Color(0xFF7A2614)),
                    center = center + Offset(-core * 0.15f, -core * 0.2f),
                    radius = core
                ),
                radius = core,
                center = center
            )
            val sparkAngle = turn * if (state == VoiceOrbState.Thinking) 1.6f else 0.7f
            val sparkOrbit = core * 1.35f
            drawCircle(
                color = Amber,
                radius = radius * (0.045f + level * 0.03f),
                center = Offset(
                    center.x + cos(sparkAngle) * sparkOrbit,
                    center.y + sin(sparkAngle) * sparkOrbit * 0.72f
                )
            )
        }
    }
}
