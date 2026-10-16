package dev.chungjungsoo.gptmobile.presentation.ui.bot

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.Bot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotSwitcherSheet(
    bots: List<Bot>,
    selectedBotUid: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.choose_bot),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        BotPickerList(
            bots = bots,
            selectedBotUid = selectedBotUid,
            onSelect = { botUid ->
                onSelect(botUid)
                onDismiss()
            }
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
    Column(modifier = modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(null) },
            headlineContent = { Text(stringResource(R.string.no_bot)) },
            supportingContent = { Text(stringResource(R.string.no_bot_description)) },
            trailingContent = {
                if (selectedBotUid == null) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                }
            }
        )
        bots.forEach { bot ->
            ListItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(bot.botUid) },
                leadingContent = {
                    BotAvatar(emoji = bot.avatarEmoji, colorHex = bot.avatarColor)
                },
                headlineContent = { Text(bot.name) },
                supportingContent = {
                    Text(
                        text = bot.instructions,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                trailingContent = {
                    if (bot.botUid == selectedBotUid) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                }
            )
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        prompts.forEach { prompt ->
            SuggestionChip(
                onClick = { onPromptClick(prompt) },
                label = { Text(prompt, maxLines = 1) }
            )
        }
    }
}
