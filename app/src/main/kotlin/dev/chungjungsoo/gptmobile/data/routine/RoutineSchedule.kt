package dev.chungjungsoo.gptmobile.data.routine

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class ScheduleKind {
    DAILY,
    WEEKLY,
    CRON
}

data class RoutineScheduleSpec(
    val kind: ScheduleKind,
    val hour: Int,
    val minute: Int,
    val dayOfWeek: Int,
    val cron: String?
)

object RoutineSchedule {
    fun nextRunMillis(spec: RoutineScheduleSpec, nowMillis: Long, zone: ZoneId): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        val next = when (spec.kind) {
            ScheduleKind.DAILY -> nextDaily(now, spec.hour, spec.minute)
            ScheduleKind.WEEKLY -> nextWeekly(now, spec.dayOfWeek, spec.hour, spec.minute)
            ScheduleKind.CRON -> nextCron(now, parseCron(spec.cron ?: throw IllegalArgumentException("Cron expression is required.")))
        }
        return next.toInstant().toEpochMilli()
    }

    fun delayUntilNext(spec: RoutineScheduleSpec, nowMillis: Long, zone: ZoneId): Long = (nextRunMillis(spec, nowMillis, zone) - nowMillis).coerceAtLeast(0L)

    private fun nextDaily(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        require(hour in 0..23 && minute in 0..59) { "Daily time is invalid." }
        val candidate = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
    }

    private fun nextWeekly(now: ZonedDateTime, dayOfWeek: Int, hour: Int, minute: Int): ZonedDateTime {
        require(dayOfWeek in 1..7) { "Weekly day is invalid." }
        require(hour in 0..23 && minute in 0..59) { "Weekly time is invalid." }
        val target = DayOfWeek.of(dayOfWeek)
        var candidate = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        val days = (target.value - now.dayOfWeek.value + 7) % 7
        candidate = candidate.plusDays(days.toLong())
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(7)
        return candidate
    }

    private fun nextCron(now: ZonedDateTime, cron: CronExpression): ZonedDateTime {
        var cursor = now.withSecond(0).withNano(0).plusMinutes(1)
        repeat(MAX_MINUTES) {
            if (cron.matches(cursor)) return cursor
            cursor = cursor.plusMinutes(1)
        }
        throw IllegalArgumentException("Cron expression has no run in the next year.")
    }

    fun parseCron(expression: String): CronExpression {
        val fields = expression.trim().split(Regex("\\s+"))
        require(fields.size == 5) { "Cron expression must have 5 fields." }
        return CronExpression(
            minute = cronField(fields[0], 0, 59),
            hour = cronField(fields[1], 0, 23),
            dayOfMonth = cronField(fields[2], 1, 31),
            month = cronField(fields[3], 1, 12),
            dayOfWeek = cronField(fields[4], 0, 7)
        )
    }

    private fun cronField(raw: String, min: Int, max: Int): Set<Int>? {
        if (raw == "*") return null
        val values = mutableSetOf<Int>()
        raw.split(",").forEach { part ->
            val stepSplit = part.split("/")
            require(stepSplit.size in 1..2) { "Cron field '$raw' is invalid." }
            val step = if (stepSplit.size == 2) stepSplit[1].toIntOrNull() ?: throw IllegalArgumentException("Cron field '$raw' is invalid.") else 1
            require(step > 0) { "Cron field '$raw' is invalid." }
            val range = stepSplit[0]
            val (start, end) = when {
                range == "*" -> min to max
                range.contains("-") -> {
                    val bounds = range.split("-")
                    require(bounds.size == 2) { "Cron field '$raw' is invalid." }
                    val from = bounds[0].toIntOrNull() ?: throw IllegalArgumentException("Cron field '$raw' is invalid.")
                    val to = bounds[1].toIntOrNull() ?: throw IllegalArgumentException("Cron field '$raw' is invalid.")
                    from to to
                }
                else -> {
                    val single = range.toIntOrNull() ?: throw IllegalArgumentException("Cron field '$raw' is invalid.")
                    if (stepSplit.size == 1) {
                        require(single in min..max) { "Cron field '$raw' is invalid." }
                        values += single
                        return@forEach
                    }
                    single to max
                }
            }
            require(start in min..max && end in min..max && start <= end) { "Cron field '$raw' is invalid." }
            var value = start
            while (value <= end) {
                values += value
                value += step
            }
        }
        require(values.isNotEmpty()) { "Cron field '$raw' is invalid." }
        return values
    }

    private const val MAX_MINUTES = 366 * 24 * 60
}

data class CronExpression(
    val minute: Set<Int>?,
    val hour: Set<Int>?,
    val dayOfMonth: Set<Int>?,
    val month: Set<Int>?,
    val dayOfWeek: Set<Int>?
) {
    fun matches(time: ZonedDateTime): Boolean {
        if (minute != null && time.minute !in minute) return false
        if (hour != null && time.hour !in hour) return false
        if (month != null && time.monthValue !in month) return false
        val domRestricted = dayOfMonth != null
        val dowRestricted = dayOfWeek != null
        val dom = !domRestricted || time.dayOfMonth in dayOfMonth!!
        val cronDow = if (time.dayOfWeek == DayOfWeek.SUNDAY) 0 else time.dayOfWeek.value
        val dow = !dowRestricted || cronDow in dayOfWeek!! || (cronDow == 0 && 7 in dayOfWeek)
        return if (domRestricted && dowRestricted) dom || dow else dom && dow
    }
}
