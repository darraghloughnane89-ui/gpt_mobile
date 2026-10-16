package dev.chungjungsoo.gptmobile.presentation.ui.routine

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineEntity
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineRunEntity
import dev.chungjungsoo.gptmobile.data.routine.ScheduleKind
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onNavigationClick: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    var historyId by rememberSaveable { mutableStateOf<String?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val nothingEnabled = state.templates.none { it.enabled } && state.routines.isEmpty()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routines)) },
                navigationIcon = {
                    IconButton(onClick = onNavigationClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editorOpen = true }) {
                Text("+")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (nothingEnabled) {
                Text(stringResource(R.string.empty_routines_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.empty_routines_body), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.routine_templates), style = MaterialTheme.typography.titleMedium)
            state.templates.forEach { template ->
                TemplateCard(
                    card = template,
                    onEnabled = { enabled ->
                        if (enabled &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.setTemplateEnabled(template.id, enabled)
                    },
                    onEdit = { editingTemplateId = template.id }
                )
            }
            if (state.routines.isNotEmpty()) {
                Text(stringResource(R.string.routine_yours), style = MaterialTheme.typography.titleMedium)
            }
            state.routines.forEach { routine ->
                RoutineCard(
                    routine = routine,
                    onRun = { viewModel.runNow(routine.routineUid) },
                    onHistory = { historyId = routine.routineUid },
                    onDelete = { viewModel.delete(routine.routineUid) }
                )
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (editorOpen) {
        RoutineEditorDialog(
            platforms = state.platforms,
            onDismiss = { editorOpen = false },
            onSave = { title, prompt, platformUid, kind, hour, minute, day, cron, plugins ->
                viewModel.save(title, prompt, platformUid, kind, hour, minute, day, cron, plugins)
                editorOpen = false
            }
        )
    }
    editingTemplateId?.let { id ->
        val card = state.templates.firstOrNull { it.id == id }
        if (card != null) {
            key(card.id) {
                TemplateEditorDialog(
                    card = card,
                    onDismiss = { editingTemplateId = null },
                    onSave = { prompt, hour, minute, day, location ->
                        viewModel.updateTemplate(card.id, prompt, hour, minute, day, location)
                        editingTemplateId = null
                    }
                )
            }
        }
    }
    LaunchedEffect(historyId) {
        historyId?.let(viewModel::loadHistory)
    }
    historyId?.let { id ->
        val runs = state.history[id].orEmpty()
        AlertDialog(
            onDismissRequest = { historyId = null },
            title = { Text(stringResource(R.string.routine_history)) },
            text = {
                Column {
                    if (runs.isEmpty()) Text(stringResource(R.string.routine_history_empty))
                    runs.forEach { run -> HistoryRow(run) }
                }
            },
            confirmButton = { TextButton(onClick = { historyId = null }) { Text(stringResource(R.string.dismiss)) } }
        )
    }
}

@Composable
private fun TemplateCard(
    card: RoutineTemplateCard,
    onEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    val enableLabel = stringResource(R.string.routine_template_enable)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(card.titleRes), style = MaterialTheme.typography.titleSmall)
                Text(scheduleText(card), style = MaterialTheme.typography.labelMedium)
            }
            Switch(
                checked = card.enabled,
                onCheckedChange = onEnabled,
                modifier = Modifier.semantics { contentDescription = enableLabel }
            )
        }
        Text(stringResource(card.summaryRes), style = MaterialTheme.typography.bodyMedium)
        if (card.weather && !card.location.isNullOrBlank()) {
            Text(
                stringResource(R.string.routine_weather_for, card.location),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            stringResource(R.string.routine_draft_only_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
    }
}

@Composable
private fun scheduleText(card: RoutineTemplateCard): String {
    val clock = String.format(Locale.US, "%02d:%02d", card.hour, card.minute)
    return when (card.kind) {
        ScheduleKind.DAILY -> stringResource(R.string.routine_schedule_daily, clock)
        ScheduleKind.WEEKLY -> stringResource(R.string.routine_schedule_weekly, weekdayName(card.dayOfWeek), clock)
        ScheduleKind.CRON -> stringResource(R.string.routine_cron)
    }
}

@Composable
private fun weekdayName(day: Int): String = when (day) {
    1 -> stringResource(R.string.routine_weekday_monday)
    2 -> stringResource(R.string.routine_weekday_tuesday)
    3 -> stringResource(R.string.routine_weekday_wednesday)
    4 -> stringResource(R.string.routine_weekday_thursday)
    5 -> stringResource(R.string.routine_weekday_friday)
    6 -> stringResource(R.string.routine_weekday_saturday)
    else -> stringResource(R.string.routine_weekday_sunday)
}

@Composable
private fun RoutineCard(
    routine: RoutineEntity,
    onRun: () -> Unit,
    onHistory: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(routine.title, style = MaterialTheme.typography.titleSmall)
        Text(routine.prompt, style = MaterialTheme.typography.bodySmall, maxLines = 3)
        Text(routine.scheduleKind, style = MaterialTheme.typography.labelMedium)
        Row {
            TextButton(onClick = onRun) { Text(stringResource(R.string.routine_run_now)) }
            TextButton(onClick = onHistory) { Text(stringResource(R.string.routine_history)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
        }
    }
}

@Composable
private fun HistoryRow(run: RoutineRunEntity) {
    Text("${run.status}: ${(run.resultText ?: run.errorText).orEmpty().take(180)}")
}

@Composable
private fun TemplateEditorDialog(
    card: RoutineTemplateCard,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int, String) -> Unit
) {
    var prompt by rememberSaveable(card.id) { mutableStateOf(card.prompt) }
    var hour by rememberSaveable(card.id) { mutableStateOf(card.hour.toString()) }
    var minute by rememberSaveable(card.id) { mutableStateOf(card.minute.toString()) }
    var day by rememberSaveable(card.id) { mutableStateOf(card.dayOfWeek.toString()) }
    var location by rememberSaveable(card.id) { mutableStateOf(card.location.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(card.titleRes)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.routine_prompt)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hour,
                    onValueChange = { hour = it },
                    label = { Text(stringResource(R.string.routine_hour)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = minute,
                    onValueChange = { minute = it },
                    label = { Text(stringResource(R.string.routine_minute)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (card.kind == ScheduleKind.WEEKLY) {
                    OutlinedTextField(
                        value = day,
                        onValueChange = { day = it },
                        label = { Text(stringResource(R.string.routine_day)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (card.weather) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text(stringResource(R.string.routine_weather_place)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(stringResource(R.string.routine_draft_only_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        prompt,
                        hour.toIntOrNull() ?: card.hour,
                        minute.toIntOrNull() ?: card.minute,
                        day.toIntOrNull() ?: card.dayOfWeek,
                        location
                    )
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun RoutineEditorDialog(
    platforms: List<PlatformV2>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, ScheduleKind, Int, Int, Int, String, String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var platformUid by rememberSaveable { mutableStateOf(platforms.firstOrNull()?.uid.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(ScheduleKind.DAILY) }
    var hour by rememberSaveable { mutableStateOf("8") }
    var minute by rememberSaveable { mutableStateOf("0") }
    var day by rememberSaveable { mutableStateOf("1") }
    var cron by rememberSaveable { mutableStateOf("0 8 * * *") }
    var plugins by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routine_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.name)) })
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text(stringResource(R.string.routine_prompt)) })
                platforms.forEach { platform ->
                    FilterChip(
                        selected = platformUid == platform.uid,
                        onClick = { platformUid = platform.uid },
                        label = { Text(platform.name) }
                    )
                }
                Row {
                    FilterChip(selected = kind == ScheduleKind.DAILY, onClick = { kind = ScheduleKind.DAILY }, label = { Text(stringResource(R.string.routine_daily)) })
                    FilterChip(selected = kind == ScheduleKind.WEEKLY, onClick = { kind = ScheduleKind.WEEKLY }, label = { Text(stringResource(R.string.routine_weekly)) })
                    FilterChip(selected = kind == ScheduleKind.CRON, onClick = { kind = ScheduleKind.CRON }, label = { Text(stringResource(R.string.routine_cron)) })
                }
                if (kind == ScheduleKind.CRON) {
                    OutlinedTextField(value = cron, onValueChange = { cron = it }, label = { Text(stringResource(R.string.routine_cron)) })
                } else {
                    OutlinedTextField(value = hour, onValueChange = { hour = it }, label = { Text(stringResource(R.string.routine_hour)) })
                    OutlinedTextField(value = minute, onValueChange = { minute = it }, label = { Text(stringResource(R.string.routine_minute)) })
                    if (kind == ScheduleKind.WEEKLY) {
                        OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text(stringResource(R.string.routine_day)) })
                    }
                }
                OutlinedTextField(value = plugins, onValueChange = { plugins = it }, label = { Text(stringResource(R.string.routine_plugins)) })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title, prompt, platformUid, kind, hour.toIntOrNull() ?: 8, minute.toIntOrNull() ?: 0, day.toIntOrNull() ?: 1, cron, plugins)
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
