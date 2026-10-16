package dev.chungjungsoo.gptmobile.presentation.ui.voice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.voice.VoicePhase
import dev.chungjungsoo.gptmobile.data.voice.VoiceSessionState

@Composable
fun VoiceScreen(
    state: VoiceSessionState,
    permissionGranted: Boolean,
    onMute: () -> Unit,
    onUnmute: () -> Unit,
    onInterrupt: () -> Unit,
    onEnd: () -> Unit,
    onOrbClick: () -> Unit,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = voiceStatusLabel(state, permissionGranted),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("voice_status")
            )
            if (state.usingHeadset) {
                Text(
                    text = stringResource(R.string.voice_headset_connected),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            state.statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            if (permissionGranted) {
                VoiceOrb(
                    state = state,
                    onClick = onOrbClick
                )
            } else {
                PermissionPrompt(onGrantPermission)
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = state.partialTranscript.ifBlank { state.lastUserUtterance },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_partial")
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = state.replyText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("voice_reply")
            )
            Text(
                text = stringResource(R.string.voice_turns_saved),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            VoiceControls(
                muted = state.muted,
                interruptEnabled = state.phase == VoicePhase.SPEAKING || state.phase == VoicePhase.THINKING,
                onMute = onMute,
                onUnmute = onUnmute,
                onInterrupt = onInterrupt,
                onEnd = onEnd
            )
        }
    }
}

@Composable
private fun VoiceOrb(
    state: VoiceSessionState,
    onClick: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "voice-orb")
    val animated by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (state.phase == VoicePhase.LISTENING || state.phase == VoicePhase.SPEAKING) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice-orb-scale"
    )
    val levelScale = 1f + state.level.coerceIn(0f, 1f) * 0.18f
    val orbDescription = stringResource(R.string.voice_orb)
    Box(
        modifier = Modifier
            .size(196.dp)
            .scale(animated * levelScale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .semantics { contentDescription = orbDescription }
            .testTag("voice_orb"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(72.dp)
        )
    }
}

@Composable
private fun PermissionPrompt(onGrantPermission: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.voice_permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onGrantPermission) {
            Text(stringResource(R.string.voice_permission_grant))
        }
    }
}

@Composable
private fun VoiceControls(
    muted: Boolean,
    interruptEnabled: Boolean,
    onMute: () -> Unit,
    onUnmute: () -> Unit,
    onInterrupt: () -> Unit,
    onEnd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val muteDescription = stringResource(if (muted) R.string.voice_unmute else R.string.voice_mute)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = if (muted) onUnmute else onMute,
                modifier = Modifier.testTag("voice_mute")
            ) {
                Icon(
                    imageVector = if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = muteDescription
                )
            }
            Text(muteDescription, style = MaterialTheme.typography.labelMedium)
        }
        val interruptDescription = stringResource(R.string.voice_interrupt)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onInterrupt,
                enabled = interruptEnabled,
                modifier = Modifier.testTag("voice_interrupt")
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = interruptDescription
                )
            }
            Text(interruptDescription, style = MaterialTheme.typography.labelMedium)
        }
        val endDescription = stringResource(R.string.voice_end)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onEnd,
                modifier = Modifier.testTag("voice_end")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = endDescription
                )
            }
            Text(endDescription, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun voiceStatusLabel(state: VoiceSessionState, permissionGranted: Boolean): String {
    if (!permissionGranted) return stringResource(R.string.voice_permission_title)
    if (state.muted && state.phase == VoicePhase.LISTENING) return stringResource(R.string.voice_status_muted)
    return when (state.phase) {
        VoicePhase.IDLE, VoicePhase.ENDED -> stringResource(R.string.voice_status_ready)
        VoicePhase.LISTENING -> stringResource(R.string.voice_status_listening)
        VoicePhase.THINKING -> stringResource(R.string.voice_status_thinking)
        VoicePhase.SPEAKING -> stringResource(R.string.voice_status_speaking)
        VoicePhase.PAUSED -> stringResource(R.string.voice_status_paused)
    }
}
