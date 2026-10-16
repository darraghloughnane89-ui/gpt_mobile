package dev.chungjungsoo.gptmobile.presentation.ui.bot

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.data.bot.BotAvatarPalette
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.HapticEvent
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.rememberHaptic
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.snapSpring

fun botAvatarColor(hex: String?): Color {
    val sanitized = BotAvatarPalette.sanitize(hex).removePrefix("#")
    return Color(0xFF000000 or sanitized.toLong(16))
}

@Composable
fun BotAvatar(
    emoji: String?,
    colorHex: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(botAvatarColor(colorHex)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji?.takeIf { it.isNotBlank() } ?: "•",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun BotColorSwatch(
    colorHex: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 1.dp,
        animationSpec = snapSpring(),
        label = "swatch"
    )
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(botAvatarColor(colorHex))
            .border(borderWidth, borderColor, CircleShape)
            .clickable(role = Role.RadioButton, onClick = {
                haptic(HapticEvent.Tick)
                onClick()
            })
    )
}
