package dev.chungjungsoo.gptmobile.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.Mascot
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.MascotMood
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.Space

@Composable
fun EmptyErrorState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    isError: Boolean = false,
    animated: Boolean = true
) {
    val stateSemantics = if (isError) {
        Modifier.semantics {
            error(description)
            liveRegion = LiveRegionMode.Assertive
        }
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .then(stateSemantics)
            .fillMaxWidth()
            .padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Mascot(
            mood = if (isError) MascotMood.Peek else MascotMood.Wave,
            animated = animated,
            modifier = Modifier.padding(bottom = Space.md)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Space.sm))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (primaryActionLabel != null && onPrimaryAction != null) {
            Spacer(modifier = Modifier.height(Space.xl))
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(primaryActionLabel)
            }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(modifier = Modifier.height(Space.sm))
            TextButton(
                onClick = onSecondaryAction,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(secondaryActionLabel)
            }
        }
    }
}
