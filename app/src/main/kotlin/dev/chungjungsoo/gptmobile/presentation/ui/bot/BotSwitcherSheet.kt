package dev.chungjungsoo.gptmobile.presentation.ui.bot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.HapticEvent
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.SectionLabel
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.Space
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.SurfaceCard
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.rememberHaptic
import dev.chungjungsoo.gptmobile.presentation.ui.designsystem.snapSpring

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotSwitcherSheet(
    bots: List<Bot>,
    selectedBotUid: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = rememberHaptic()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SectionLabel(text = stringResource(R.string.choose_bot))
        BotPickerList(
            bots = bots,
            selectedBotUid = selectedBotUid,
            onSelect = { botUid ->
                haptic(HapticEvent.Tick)
                onSelect(botUid)
                onDismiss()
            },
            modifier = Modifier.padding(bottom = Space.xl)
        )
    }
}

@Composable
fun BotPickerList(
    bots: List<Bot>,
    selectedBotUid: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        BotPickerRow(
            title = stringResource(R.string.no_bot),
            body = stringResource(R.string.no_bot_description),
            selected = selectedBotUid == null,
            avatar = null,
            onClick = { onSelect(null) }
        )
        bots.forEach { bot ->
            BotPickerRow(
                title = bot.name,
                body = bot.instructions,
                selected = bot.botUid == selectedBotUid,
                avatar = { BotAvatar(emoji = bot.avatarEmoji, colorHex = bot.avatarColor) },
                onClick = { onSelect(bot.botUid) }
            )
        }
    }
}

@Composable
private fun BotPickerRow(
    title: String,
    body: String,
    selected: Boolean,
    avatar: (@Composable () -> Unit)?,
    onClick: () -> Unit
) {
    SurfaceCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            avatar?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(snapSpring()),
                exit = scaleOut(snapSpring())
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun BotStarterPromptRow(
    prompts: List<String>,
    onPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (prompts.isEmpty()) return
    val haptic = rememberHaptic()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.md, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        prompts.forEach { prompt ->
            SuggestionChip(
                onClick = {
                    haptic(HapticEvent.Tick)
                    onPromptClick(prompt)
                },
                label = { Text(prompt, maxLines = 1) }
            )
        }
    }
}
