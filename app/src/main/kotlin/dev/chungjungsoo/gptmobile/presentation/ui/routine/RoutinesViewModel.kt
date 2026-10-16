package dev.chungjungsoo.gptmobile.presentation.ui.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PluginRoutineDao
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineEntity
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineRunEntity
import dev.chungjungsoo.gptmobile.data.plugin.parseRoutineReadAllowlist
import dev.chungjungsoo.gptmobile.data.routine.RoutineRunner
import dev.chungjungsoo.gptmobile.data.routine.RoutineScheduler
import dev.chungjungsoo.gptmobile.data.routine.ScheduleKind
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutinesUiState(
    val routines: List<RoutineEntity> = emptyList(),
    val platforms: List<PlatformV2> = emptyList(),
    val history: Map<String, List<RoutineRunEntity>> = emptyMap(),
    val message: String? = null
)

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val dao: PluginRoutineDao,
    private val platforms: PlatformV2Dao,
    private val scheduler: RoutineScheduler,
    private val runner: RoutineRunner
) : ViewModel() {
    private val _state = MutableStateFlow(RoutinesUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeRoutines().collect { routines ->
                _state.update { it.copy(routines = routines, platforms = platforms.getPlatforms()) }
            }
        }
    }

    fun save(
        title: String,
        prompt: String,
        platformUid: String,
        kind: ScheduleKind,
        hour: Int,
        minute: Int,
        dayOfWeek: Int,
        cron: String,
        pluginUids: String,
        allowedReadTools: String = ""
    ) {
        viewModelScope.launch {
            runCatching {
                if (kind == ScheduleKind.CRON) {
                    dev.chungjungsoo.gptmobile.data.routine.RoutineSchedule.parseCron(cron)
                }
                val now = System.currentTimeMillis() / 1000
                val routine = RoutineEntity(
                    routineUid = UUID.randomUUID().toString(),
                    title = title.ifBlank { "Routine" },
                    prompt = prompt,
                    botUid = null,
                    platformUid = platformUid,
                    scheduleKind = kind.name,
                    cronExpression = cron.takeIf { kind == ScheduleKind.CRON },
                    hour = hour,
                    minute = minute,
                    dayOfWeek = dayOfWeek,
                    pluginUids = pluginUids,
                    allowedReadTools = parseRoutineReadAllowlist(allowedReadTools).joinToString(","),
                    enabled = true,
                    chatId = null,
                    createdAt = now,
                    updatedAt = now
                )
                dao.upsertRoutine(routine)
                scheduler.schedule(routine)
            }.onFailure { error -> _state.update { it.copy(message = error.message) } }
        }
    }

    fun runNow(routineUid: String) {
        viewModelScope.launch {
            runCatching { runner.run(routineUid) }
                .onFailure { error -> _state.update { it.copy(message = error.message) } }
        }
    }

    fun delete(routineUid: String) {
        viewModelScope.launch {
            scheduler.cancel(routineUid)
            dao.deleteRoutine(routineUid)
        }
    }

    fun loadHistory(routineUid: String) {
        viewModelScope.launch {
            dao.observeRuns(routineUid).collect { runs ->
                _state.update { it.copy(history = it.history + (routineUid to runs)) }
            }
        }
    }
}
