package dev.chungjungsoo.gptmobile.data.routine

import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.PluginEntity
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineEntity
import dev.chungjungsoo.gptmobile.data.plugin.PluginCatalog
import java.time.ZoneOffset

data class RoutineTemplate(
    val id: String,
    val titleRes: Int,
    val summaryRes: Int,
    val title: String,
    val prompt: String,
    val kind: ScheduleKind,
    val hour: Int,
    val minute: Int,
    val dayOfWeek: Int,
    val catalogPluginIds: Set<String>,
    val weather: Boolean
)

data class RoutineTemplateOverride(
    val prompt: String? = null,
    val hour: Int? = null,
    val minute: Int? = null,
    val dayOfWeek: Int? = null,
    val location: String? = null
)

data class ResolvedRoutineTemplate(
    val template: RoutineTemplate,
    val prompt: String,
    val hour: Int,
    val minute: Int,
    val dayOfWeek: Int,
    val location: String?
)

object RoutineTemplates {
    const val MORNING_ID = "template:morning-briefing"
    const val EVENING_ID = "template:evening-wrap-up"
    const val WEEKLY_ID = "template:weekly-review"
    const val DEFAULT_WEATHER_PLACE = "Newbawn, Co. Wexford"
    const val DRAFT_ONLY_RULE =
        "Never send, post, reply, forward, or publish a message or email. Drafting only. The person sends it."

    private val mailAndCalendar = setOf(PluginCatalog.gmail.id, PluginCatalog.calendar.id)

    val morning = RoutineTemplate(
        id = MORNING_ID,
        titleRes = R.string.routine_template_morning_title,
        summaryRes = R.string.routine_template_morning_summary,
        title = "Morning briefing",
        prompt = """
            Prepare a morning briefing.

            Read today's calendar events. Read unread mail that looks important: starred, primary, or from a person. If Calendar or Gmail is not connected, say so in one line and continue.

            Weather is supplied below for the named place. Summarise it. Do not look the weather up yourself.

            Keep it short: the weather, today's events, and the mail that matters. If a reply would help, write the draft in this chat.

            $DRAFT_ONLY_RULE
        """.trimIndent(),
        kind = ScheduleKind.DAILY,
        hour = 7,
        minute = 30,
        dayOfWeek = 1,
        catalogPluginIds = mailAndCalendar,
        weather = true
    )

    val evening = RoutineTemplate(
        id = EVENING_ID,
        titleRes = R.string.routine_template_evening_title,
        summaryRes = R.string.routine_template_evening_summary,
        title = "Evening wrap-up",
        prompt = """
            Evening wrap-up.

            Using read-only tools, look at today's calendar and today's mail. Say what got done, what is still open, and what can wait until tomorrow.

            If a reply or message would help, write the draft in this chat.

            $DRAFT_ONLY_RULE
        """.trimIndent(),
        kind = ScheduleKind.DAILY,
        hour = 21,
        minute = 0,
        dayOfWeek = 1,
        catalogPluginIds = mailAndCalendar,
        weather = false
    )

    val weekly = RoutineTemplate(
        id = WEEKLY_ID,
        titleRes = R.string.routine_template_weekly_title,
        summaryRes = R.string.routine_template_weekly_summary,
        title = "Weekly review",
        prompt = """
            Weekly review.

            Using read-only tools, look over the past week on the calendar and in mail. Summarise what moved, what stalled, and the three things that matter next week.

            If a reply or message would help, write the draft in this chat.

            $DRAFT_ONLY_RULE
        """.trimIndent(),
        kind = ScheduleKind.WEEKLY,
        hour = 18,
        minute = 0,
        dayOfWeek = 7,
        catalogPluginIds = mailAndCalendar,
        weather = false
    )

    val all = listOf(morning, evening, weekly)

    fun find(id: String): RoutineTemplate? = all.firstOrNull { it.id == id }
}

fun RoutineTemplate.resolve(override: RoutineTemplateOverride?): ResolvedRoutineTemplate {
    val location = if (weather) {
        override?.location?.trim()?.takeIf { it.isNotEmpty() } ?: RoutineTemplates.DEFAULT_WEATHER_PLACE
    } else {
        null
    }
    return ResolvedRoutineTemplate(
        template = this,
        prompt = override?.prompt?.takeIf { it.isNotBlank() } ?: prompt,
        hour = override?.hour ?: hour,
        minute = override?.minute ?: minute,
        dayOfWeek = override?.dayOfWeek ?: dayOfWeek,
        location = location
    )
}

fun ResolvedRoutineTemplate.toRoutineEntity(
    title: String,
    platformUid: String,
    pluginUids: String,
    enabled: Boolean,
    existing: RoutineEntity?,
    nowSeconds: Long
): RoutineEntity = RoutineEntity(
    routineUid = template.id,
    title = title,
    prompt = prompt,
    botUid = existing?.botUid,
    platformUid = platformUid,
    scheduleKind = template.kind.name,
    cronExpression = null,
    hour = hour,
    minute = minute,
    dayOfWeek = dayOfWeek,
    pluginUids = pluginUids,
    enabled = enabled,
    chatId = existing?.chatId,
    createdAt = existing?.createdAt ?: nowSeconds,
    updatedAt = nowSeconds
)

fun pluginUidsFor(catalogIds: Set<String>, plugins: List<PluginEntity>): String = plugins
    .filter { plugin -> plugin.enabled && plugin.catalogId != null && plugin.catalogId in catalogIds }
    .joinToString(",") { it.pluginUid }

fun assembleRoutineTask(prompt: String, weatherSummary: String?): String {
    val rule = RoutineTemplates.DRAFT_ONLY_RULE
    val base = prompt.trim().removeSuffix(rule).trim()
    val weather = weatherSummary?.trim()?.takeIf { it.isNotEmpty() }?.let { "\n\nWeather (Open-Meteo):\n$it" }.orEmpty()
    return "$base$weather\n\n$rule"
}

fun validateTemplateTime(kind: ScheduleKind, hour: Int, minute: Int, dayOfWeek: Int) {
    RoutineSchedule.nextRunMillis(
        RoutineScheduleSpec(kind, hour, minute, dayOfWeek, cron = null),
        nowMillis = 0L,
        zone = ZoneOffset.UTC
    )
}
