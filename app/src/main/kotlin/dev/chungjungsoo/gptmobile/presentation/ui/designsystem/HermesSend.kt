package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

enum class HermesSendState {
    Idle,
    Armed,
    Sending,
    Sent
}

fun hermesSendState(canSend: Boolean, isSending: Boolean, sent: Boolean = false): HermesSendState = when {
    sent -> HermesSendState.Sent
    isSending -> HermesSendState.Sending
    canSend -> HermesSendState.Armed
    else -> HermesSendState.Idle
}

@Composable
fun HermesSendAction(
    state: HermesSendState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    val haptic = rememberHaptic()
    val description = if (state == HermesSendState.Sending) {
        stringResource(R.string.cancel_active_runs)
    } else {
        contentDescription ?: stringResource(R.string.send)
    }
    val filled = state == HermesSendState.Armed || state == HermesSendState.Sending || state == HermesSendState.Sent
    val scale by animateFloatAsState(
        targetValue = if (state == HermesSendState.Sending) 0.94f else 1f,
        animationSpec = playfulSpring(),
        label = "hermes scale"
    )
    IconButton(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled && state != HermesSendState.Idle && state != HermesSendState.Sent,
        onClick = {
            haptic(if (state == HermesSendState.Sending) HapticEvent.Reject else HapticEvent.Confirm)
            onClick()
        }
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (filled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(fadeSpring()) + scaleIn(snapSpring(), initialScale = 0.7f)) togetherWith
                        (fadeOut(fadeSpring()) + scaleOut(snapSpring(), targetScale = 0.7f))
                },
                label = "hermes glyph"
            ) { target ->
                val icon = when (target) {
                    HermesSendState.Sending -> Icons.Filled.Stop
                    HermesSendState.Sent -> Icons.Filled.Check
                    HermesSendState.Idle, HermesSendState.Armed -> Icons.Rounded.ArrowUpward
                }
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = if (filled) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
