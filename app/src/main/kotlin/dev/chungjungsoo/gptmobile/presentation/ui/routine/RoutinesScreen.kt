package dev.chungjungsoo.gptmobile.presentation.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineEntity
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineRunEntity
import dev.chungjungsoo.gptmobile.data.routine.ScheduleKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onNavigationClick: () -> Unit,
    viewModel: RoutinesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var historyId by rememberSaveable { mutableStateOf<String?>(null) }
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
            if (state.routines.isEmpty()) {
                Text(stringResource(R.string.routines_empty))
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
            onSave = { title, prompt, platformUid, kind, hour, minute, day, cron, plugins, readTools ->
                viewModel.save(title, prompt, platformUid, kind, hour, minute, day, cron, plugins, readTools)
                editorOpen = false
            }
        )
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
private fun RoutineEditorDialog(
    platforms: List<PlatformV2>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, ScheduleKind, Int, Int, Int, String, String, String) -> Unit
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
    var readTools by rememberSaveable { mutableStateOf("") }
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
                OutlinedTextField(
                    value = readTools,
                    onValueChange = { readTools = it },
                    label = { Text(stringResource(R.string.routine_read_tools)) },
                    supportingText = { Text(stringResource(R.string.routine_read_tools_hint)) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title,
                        prompt,
                        platformUid,
                        kind,
                        hour.toIntOrNull() ?: 8,
                        minute.toIntOrNull() ?: 0,
                        day.toIntOrNull() ?: 1,
                        cron,
                        plugins,
                        readTools
                    )
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
