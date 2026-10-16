package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chungjungsoo.gptmobile.R
import kotlinx.coroutines.launch

@Composable
private fun paletteEntries(): List<Pair<AppDestination, String>> = listOf(
    AppDestination.Chats to stringResource(R.string.chats),
    AppDestination.Voice to stringResource(R.string.nav_voice),
    AppDestination.Projects to stringResource(R.string.nav_projects),
    AppDestination.Bots to stringResource(R.string.nav_bots),
    AppDestination.Skills to stringResource(R.string.nav_skills),
    AppDestination.Memory to stringResource(R.string.nav_memory),
    AppDestination.Review to stringResource(R.string.nav_review),
    AppDestination.Settings to stringResource(R.string.settings),
    AppDestination.Usage to stringResource(R.string.nav_usage),
    AppDestination.Obsidian to stringResource(R.string.nav_obsidian)
)

@Composable
private fun DrawerSection(
    section: AppSection,
    current: AppDestination,
    onDestination: (AppDestination) -> Unit
) {
    val items = drawerDestinations().filter { it.section() == section }
    if (items.isEmpty()) return
    SectionLabel(section.title())
    items.forEach { destination ->
        NavigationDrawerItem(
            modifier = Modifier.padding(horizontal = Space.md),
            selected = destination == current,
            onClick = { onDestination(destination) },
            icon = { Icon(destination.icon(), contentDescription = null) },
            label = { Text(destination.title()) }
        )
    }
}

@Composable
fun AppDestination.title(): String = when (this) {
    AppDestination.Chats -> stringResource(R.string.chats)
    AppDestination.Voice -> stringResource(R.string.nav_voice)
    AppDestination.Projects -> stringResource(R.string.nav_projects)
    AppDestination.Bots -> stringResource(R.string.nav_bots)
    AppDestination.Skills -> stringResource(R.string.nav_skills)
    AppDestination.Memory -> stringResource(R.string.nav_memory)
    AppDestination.Review -> stringResource(R.string.nav_review)
    AppDestination.Settings -> stringResource(R.string.settings)
    AppDestination.Usage -> stringResource(R.string.nav_usage)
    AppDestination.Obsidian -> stringResource(R.string.nav_obsidian)
}

@Composable
fun AppDestination.icon(): ImageVector = when (this) {
    AppDestination.Chats -> Icons.Outlined.ChatBubbleOutline
    AppDestination.Voice -> Icons.Outlined.Mic
    AppDestination.Projects -> Icons.Outlined.Folder
    AppDestination.Bots -> Icons.Outlined.SmartToy
    AppDestination.Skills -> Icons.Outlined.AutoAwesome
    AppDestination.Memory -> Icons.Outlined.Psychology
    AppDestination.Review -> Icons.Outlined.Inbox
    AppDestination.Settings -> Icons.Outlined.Settings
    AppDestination.Usage -> Icons.Outlined.DataUsage
    AppDestination.Obsidian -> Icons.Outlined.AccountTree
}

@Composable
fun AppSection.title(): String = when (this) {
    AppSection.Talk -> stringResource(R.string.nav_section_talk)
    AppSection.Library -> stringResource(R.string.nav_section_library)
    AppSection.Inbox -> stringResource(R.string.nav_section_inbox)
    AppSection.You -> stringResource(R.string.nav_section_you)
}

@Composable
fun AppDestination.emptyTitle(): String = when (this) {
    AppDestination.Projects -> stringResource(R.string.shell_projects_title)
    AppDestination.Bots -> stringResource(R.string.shell_bots_title)
    AppDestination.Skills -> stringResource(R.string.shell_skills_title)
    AppDestination.Memory -> stringResource(R.string.shell_memory_title)
    AppDestination.Review -> stringResource(R.string.shell_review_title)
    AppDestination.Usage -> stringResource(R.string.shell_usage_title)
    AppDestination.Obsidian -> stringResource(R.string.shell_obsidian_title)
    AppDestination.Voice -> stringResource(R.string.nav_voice)
    AppDestination.Chats -> stringResource(R.string.empty_chats_title)
    AppDestination.Settings -> stringResource(R.string.settings)
}

@Composable
fun AppDestination.shellBody(): String = when (this) {
    AppDestination.Projects -> stringResource(R.string.shell_projects_body)
    AppDestination.Bots -> stringResource(R.string.shell_bots_body)
    AppDestination.Skills -> stringResource(R.string.shell_skills_body)
    AppDestination.Memory -> stringResource(R.string.shell_memory_body)
    AppDestination.Voice -> stringResource(R.string.shell_voice_body)
    AppDestination.Review -> stringResource(R.string.shell_review_body)
    AppDestination.Usage -> stringResource(R.string.shell_usage_body)
    AppDestination.Obsidian -> stringResource(R.string.shell_obsidian_body)
    AppDestination.Chats -> stringResource(R.string.empty_chats_body)
    AppDestination.Settings -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavScaffold(
    current: AppDestination,
    onDestination: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    content: @Composable (openDrawer: () -> Unit, openCommand: () -> Unit) -> Unit
) {
    val expanded = LocalConfiguration.current.screenWidthDp >= 600
    var commandOpen by remember { mutableStateOf(false) }
    val openCommand = { commandOpen = true }
    if (expanded) {
        Row(modifier = modifier.fillMaxSize()) {
            AppRail(current = current, onDestination = onDestination)
            content({}, openCommand)
        }
    } else {
        val drawerState = rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val openDrawer: () -> Unit = {
            scope.launch { drawerState.open() }
        }
        ModalNavigationDrawer(
            modifier = modifier,
            drawerState = drawerState,
            gesturesEnabled = gesturesEnabled,
            drawerContent = {
                AppDrawerSheet(
                    current = current,
                    onDestination = {
                        scope.launch { drawerState.close() }
                        onDestination(it)
                    },
                    onCommand = {
                        scope.launch { drawerState.close() }
                        commandOpen = true
                    }
                )
            }
        ) {
            content(openDrawer, openCommand)
        }
    }
    if (commandOpen) {
        CommandPalette(
            onDismiss = { commandOpen = false },
            onDestination = {
                commandOpen = false
                onDestination(it)
            }
        )
    }
}

@Composable
private fun AppRail(
    current: AppDestination,
    onDestination: (AppDestination) -> Unit
) {
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        drawerDestinations().forEach { destination ->
            NavigationRailItem(
                selected = destination == current,
                onClick = { onDestination(destination) },
                icon = { Icon(destination.icon(), contentDescription = destination.title()) },
                label = { Text(destination.title(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

@Composable
private fun AppDrawerSheet(
    current: AppDestination,
    onDestination: (AppDestination) -> Unit,
    onCommand: () -> Unit
) {
    ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.lg)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                Mascot(mood = MascotMood.Peek, modifier = Modifier.size(56.dp))
                Text(text = Brand.name(), style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                text = stringResource(R.string.command_title),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCommand)
                    .padding(horizontal = Space.xl, vertical = Space.md),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = Space.sm))
            DrawerSection(AppSection.Talk, current, onDestination)
            DrawerSection(AppSection.Library, current, onDestination)
            DrawerSection(AppSection.Inbox, current, onDestination)
            DrawerSection(AppSection.You, current, onDestination)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandPalette(
    onDismiss: () -> Unit,
    onDestination: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val titled = paletteEntries()
    val destinations = titled.filter { (_, title) ->
        query.isBlank() || title.contains(query, ignoreCase = true)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.xxl)
        ) {
            Text(
                text = stringResource(R.string.command_title),
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.md),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.command_placeholder)) }
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                items(destinations, key = { it.first.name }) { (destination, title) ->
                    val section = destination.section().title()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDestination(destination) }
                            .padding(vertical = Space.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.md)
                    ) {
                        Icon(destination.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                section,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
