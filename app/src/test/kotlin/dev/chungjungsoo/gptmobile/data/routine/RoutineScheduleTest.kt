package dev.chungjungsoo.gptmobile.data.routine

import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineScheduleTest {
    @Test
    fun `daily weekly and cron land on the next strict future slot`() {
        val zone = ZoneOffset.UTC
        val thursdayNoon = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

        val daily = RoutineSchedule.nextRunMillis(
            RoutineScheduleSpec(ScheduleKind.DAILY, hour = 8, minute = 30, dayOfWeek = 1, cron = null),
            thursdayNoon,
            zone
        )
        val weekly = RoutineSchedule.nextRunMillis(
            RoutineScheduleSpec(ScheduleKind.WEEKLY, hour = 8, minute = 30, dayOfWeek = 1, cron = null),
            thursdayNoon,
            zone
        )
        val cron = RoutineSchedule.nextRunMillis(
            RoutineScheduleSpec(ScheduleKind.CRON, hour = 0, minute = 0, dayOfWeek = 1, cron = "15 9 * * 1"),
            thursdayNoon,
            zone
        )

        assertEquals(utc(2026, 10, 16, 8, 30), daily)
        assertEquals(utc(2026, 10, 19, 8, 30), weekly)
        assertEquals(utc(2026, 10, 19, 9, 15), cron)
    }

    @Test
    fun `a slot equal to now waits until the following occurrence`() {
        val zone = ZoneOffset.UTC
        val now = ZonedDateTime.of(2026, 10, 15, 8, 30, 0, 0, zone).toInstant().toEpochMilli()
        val next = RoutineSchedule.nextRunMillis(
            RoutineScheduleSpec(ScheduleKind.DAILY, hour = 8, minute = 30, dayOfWeek = 1, cron = null),
            now,
            zone
        )
        assertEquals(utc(2026, 10, 16, 8, 30), next)
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
}
