package dev.chungjungsoo.gptmobile.presentation.ui.routine

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PluginRoutineDao
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineEntity
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineRunEntity
import dev.chungjungsoo.gptmobile.data.routine.ResolvedRoutineTemplate
import dev.chungjungsoo.gptmobile.data.routine.RoutineRunner
import dev.chungjungsoo.gptmobile.data.routine.RoutineScheduler
import dev.chungjungsoo.gptmobile.data.routine.RoutineTemplateOverride
import dev.chungjungsoo.gptmobile.data.routine.RoutineTemplateSettings
import dev.chungjungsoo.gptmobile.data.routine.RoutineTemplates
import dev.chungjungsoo.gptmobile.data.routine.ScheduleKind
import dev.chungjungsoo.gptmobile.data.routine.pluginUidsFor
import dev.chungjungsoo.gptmobile.data.routine.resolve
import dev.chungjungsoo.gptmobile.data.routine.toRoutineEntity
import dev.chungjungsoo.gptmobile.data.routine.validateTemplateTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutineTemplateCard(
    val id: String,
    val titleRes: Int,
    val summaryRes: Int,
    val prompt: String,
    val kind: ScheduleKind,
    val hour: Int,
    val minute: Int,
    val dayOfWeek: Int,
    val location: String?,
    val weather: Boolean,
    val enabled: Boolean
)

data class RoutinesUiState(
    val routines: List<RoutineEntity> = emptyList(),
    val templates: List<RoutineTemplateCard> = RoutineTemplates.all.map { it.resolve(null).toCard(enabled = false) },
    val platforms: List<PlatformV2> = emptyList(),
    val history: Map<String, List<RoutineRunEntity>> = emptyMap(),
    val message: String? = null
)

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: PluginRoutineDao,
    private val platforms: PlatformV2Dao,
    private val scheduler: RoutineScheduler,
    private val runner: RoutineRunner,
    private val templateSettings: RoutineTemplateSettings
) : ViewModel() {
    private val _state = MutableStateFlow(RoutinesUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(dao.observeRoutines(), templateSettings.observe()) { routines, overrides ->
                routines to overrides
            }.collect { (routines, overrides) ->
                val installed = platforms.getPlatforms()
                val cards = RoutineTemplates.all.map { template ->
                    val entity = routines.firstOrNull { it.routineUid == template.id }
                    template.resolve(overrides[template.id]).toCard(enabled = entity?.enabled == true)
                }
                _state.update {
                    it.copy(
                        routines = routines.filter { routine -> RoutineTemplates.find(routine.routineUid) == null },
                        templates = cards,
                        platforms = installed
                    )
                }
            }
        }
    }

    fun setTemplateEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                val template = RoutineTemplates.find(id) ?: return@runCatching
                val existing = dao.getRoutine(id)
                val resolved = template.resolve(templateSettings.observe().first()[id])
                if (enabled) {
                    validateTemplateTime(template.kind, resolved.hour, resolved.minute, resolved.dayOfWeek)
                    val platformUid = existing?.platformUid?.takeIf { it.isNotBlank() } ?: choosePlatform()
                    val entity = resolved.toRoutineEntity(
                        title = context.getString(template.titleRes),
                        platformUid = platformUid,
                        pluginUids = pluginUidsFor(template.catalogPluginIds, dao.listPlugins()),
                        enabled = true,
                        existing = existing,
                        nowSeconds = nowSeconds()
                    )
                    dao.upsertRoutine(entity)
                    scheduler.schedule(entity)
                } else if (existing != null) {
                    val disabled = existing.copy(enabled = false, updatedAt = nowSeconds())
                    dao.upsertRoutine(disabled)
                    scheduler.cancel(id)
                }
            }.onFailure { error ->
                _state.update { it.copy(message = failureText(error)) }
            }
        }
    }

    fun updateTemplate(id: String, prompt: String, hour: Int, minute: Int, dayOfWeek: Int, location: String) {
        viewModelScope.launch {
            runCatching {
                val template = RoutineTemplates.find(id) ?: return@runCatching
                validateTemplateTime(template.kind, hour, minute, dayOfWeek)
                val override = RoutineTemplateOverride(
                    prompt = prompt,
                    hour = hour,
                    minute = minute,
                    dayOfWeek = dayOfWeek,
                    location = location.takeIf { template.weather }
                )
                templateSettings.save(id, override)
                val existing = dao.getRoutine(id) ?: return@runCatching
                val resolved = template.resolve(override)
                val entity = resolved.toRoutineEntity(
                    title = context.getString(template.titleRes),
                    platformUid = existing.platformUid,
                    pluginUids = pluginUidsFor(template.catalogPluginIds, dao.listPlugins()),
                    enabled = existing.enabled,
                    existing = existing,
                    nowSeconds = nowSeconds()
                )
                dao.upsertRoutine(entity)
                scheduler.schedule(entity)
            }.onFailure { error ->
                _state.update { it.copy(message = failureText(error)) }
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
        pluginUids: String
    ) {
        viewModelScope.launch {
            runCatching {
                if (kind == ScheduleKind.CRON) {
                    dev.chungjungsoo.gptmobile.data.routine.RoutineSchedule.parseCron(cron)
                }
                val now = nowSeconds()
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

    private suspend fun choosePlatform(): String {
        val installed = platforms.getPlatforms()
        return installed.firstOrNull { it.enabled }?.uid
            ?: installed.firstOrNull()?.uid
            ?: throw IllegalStateException(context.getString(R.string.routine_needs_model))
    }

    private fun failureText(error: Throwable): String = when (error) {
        is IllegalArgumentException -> context.getString(R.string.error_routine_failed)
        else -> error.message ?: context.getString(R.string.error_routine_failed)
    }

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000
}

private fun ResolvedRoutineTemplate.toCard(enabled: Boolean) = RoutineTemplateCard(
    id = template.id,
    titleRes = template.titleRes,
    summaryRes = template.summaryRes,
    prompt = prompt,
    kind = template.kind,
    hour = hour,
    minute = minute,
    dayOfWeek = dayOfWeek,
    location = location,
    weather = template.weather,
    enabled = enabled
)
