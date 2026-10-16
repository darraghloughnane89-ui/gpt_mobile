package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.presentation.theme.CodeTextStyle

@Composable
fun CodeBlock(
    code: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    language: String? = null,
    content: @Composable (() -> Unit)? = null
) {
    val haptic = rememberHaptic()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.sm),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Elevation.hairline,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.md, vertical = Space.sm),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    modifier = Modifier.padding(end = Space.md),
                    text = language?.trim()?.takeIf { it.isNotEmpty() } ?: stringResource(R.string.code),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                TextButton(
                    modifier = Modifier.heightIn(min = 32.dp),
                    onClick = {
                        haptic(HapticEvent.Tick)
                        onCopy()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = Space.md, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.copy_code),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Box(modifier = Modifier.fillMaxWidth()) {
                if (content != null) {
                    content()
                } else {
                    Text(
                        text = code,
                        style = CodeTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = false,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = Space.lg, vertical = Space.md)
                    )
                }
            }
        }
    }
}
