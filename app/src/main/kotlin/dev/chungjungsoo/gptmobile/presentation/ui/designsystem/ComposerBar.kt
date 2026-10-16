package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R

@Composable
fun ComposerBar(
    canSend: Boolean,
    isSending: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    field: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onAttach: (() -> Unit)? = null,
    attachEnabled: Boolean = true,
    onMic: (() -> Unit)? = null,
    attachments: @Composable (() -> Unit)? = null
) {
    val showMic = onMic != null && !canSend && !isSending
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = Elevation.raised
    ) {
        Column {
            attachments?.invoke()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onAttach != null) {
                    IconButton(enabled = attachEnabled, onClick = onAttach) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_attach_file),
                            contentDescription = stringResource(R.string.attach_file)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Space.sm)
                ) {
                    field()
                }
                AnimatedContent(
                    targetState = showMic,
                    transitionSpec = {
                        (fadeIn(fadeSpring()) + scaleIn(playfulSpring(), initialScale = 0.8f)) togetherWith
                            (fadeOut(fadeSpring()) + scaleOut(playfulSpring(), targetScale = 0.8f))
                    },
                    label = "composer trailing"
                ) { mic ->
                    if (mic && onMic != null) {
                        IconButton(onClick = onMic) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = stringResource(R.string.nav_voice)
                            )
                        }
                    } else {
                        HermesSendAction(
                            state = hermesSendState(canSend = canSend, isSending = isSending),
                            onClick = if (isSending) onStop else onSend,
                            enabled = isSending || canSend
                        )
                    }
                }
            }
        }
    }
}
