package dev.chungjungsoo.gptmobile.data.routine

import dev.chungjungsoo.gptmobile.data.database.entity.PluginEntity
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineEntity
import dev.chungjungsoo.gptmobile.data.plugin.PluginCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTemplatesTest {
    @Test
    fun `gallery defaults are morning evening and weekly`() {
        assertEquals(listOf(RoutineTemplates.MORNING_ID, RoutineTemplates.EVENING_ID, RoutineTemplates.WEEKLY_ID), RoutineTemplates.all.map { it.id })

        val morning = RoutineTemplates.morning.resolve(null)
        assertEquals(ScheduleKind.DAILY, morning.template.kind)
        assertEquals(7, morning.hour)
        assertEquals(30, morning.minute)
        assertEquals(RoutineTemplates.DEFAULT_WEATHER_PLACE, morning.location)
        assertTrue(morning.template.weather)
        assertTrue(morning.prompt.contains("calendar", ignoreCase = true))
        assertTrue(morning.prompt.contains("unread", ignoreCase = true))
        assertTrue(morning.prompt.contains("important", ignoreCase = true))

        val evening = RoutineTemplates.evening.resolve(null)
        assertEquals(ScheduleKind.DAILY, evening.template.kind)
        assertEquals(21, evening.hour)
        assertEquals(0, evening.minute)
        assertNull(evening.location)

        val weekly = RoutineTemplates.weekly.resolve(null)
        assertEquals(ScheduleKind.WEEKLY, weekly.template.kind)
        assertEquals(18, weekly.hour)
        assertEquals(7, weekly.dayOfWeek)
        assertNull(weekly.location)
    }

    @Test
    fun `every template drafts and does not ask the model to send`() {
        RoutineTemplates.all.forEach { template ->
            assertTrue(template.prompt.contains(RoutineTemplates.DRAFT_ONLY_RULE))
            assertTrue(template.prompt.contains("write the draft", ignoreCase = true))
            assertFalse(template.prompt.contains("send the email", ignoreCase = true))
            assertFalse(template.prompt.contains("send this", ignoreCase = true))
            assertEquals(setOf(PluginCatalog.gmail.id, PluginCatalog.calendar.id), template.catalogPluginIds)
        }
    }

    @Test
    fun `edits override the prompt time and weather place`() {
        val edited = RoutineTemplates.morning.resolve(
            RoutineTemplateOverride(prompt = "Shorter, please.\n\n${RoutineTemplates.DRAFT_ONLY_RULE}", hour = 6, minute = 15, location = "Wexford Town")
        )
        assertEquals(6, edited.hour)
        assertEquals(15, edited.minute)
        assertEquals("Wexford Town", edited.location)
        assertTrue(edited.prompt.startsWith("Shorter"))

        val blankPlace = RoutineTemplates.morning.resolve(RoutineTemplateOverride(location = "  "))
        assertEquals(RoutineTemplates.DEFAULT_WEATHER_PLACE, blankPlace.location)
    }

    @Test
    fun `enabling a template reuses the stable id and keeps the chat`() {
        val existing = sampleRoutine(chatId = 42)
        val entity = RoutineTemplates.morning.resolve(null).toRoutineEntity(
            title = "Morning briefing",
            platformUid = "platform-1",
            pluginUids = "gmail-uid,calendar-uid",
            enabled = true,
            existing = existing,
            nowSeconds = 100
        )
        assertEquals(RoutineTemplates.MORNING_ID, entity.routineUid)
        assertEquals(42, entity.chatId)
        assertEquals(ScheduleKind.DAILY.name, entity.scheduleKind)
        assertEquals("gmail-uid,calendar-uid", entity.pluginUids)
        assertTrue(entity.enabled)
        assertEquals(existing.createdAt, entity.createdAt)
    }

    @Test
    fun `plugin ids follow enabled gmail and calendar connectors`() {
        val plugins = listOf(
            plugin("gmail-uid", PluginCatalog.gmail.id, enabled = true),
            plugin("calendar-off", PluginCatalog.calendar.id, enabled = false),
            plugin("slack-uid", PluginCatalog.slack.id, enabled = true)
        )
        assertEquals("gmail-uid", pluginUidsFor(RoutineTemplates.morning.catalogPluginIds, plugins))
    }

    @Test
    fun `weather is inserted before the draft rule and the rule is not repeated`() {
        val task = assembleRoutineTask(RoutineTemplates.morning.prompt, "Newbawn, County Wexford, Ireland\nNow: 12°C, Overcast")
        val weatherAt = task.indexOf("Weather (Open-Meteo):")
        val ruleAt = task.indexOf(RoutineTemplates.DRAFT_ONLY_RULE)
        assertTrue(weatherAt in 1 until ruleAt)
        assertEquals(ruleAt, task.lastIndexOf(RoutineTemplates.DRAFT_ONLY_RULE))
        assertTrue(task.endsWith(RoutineTemplates.DRAFT_ONLY_RULE))
    }

    @Test
    fun `a custom routine still gets the draft rule`() {
        val task = assembleRoutineTask("Summarise the inbox.", null)
        assertTrue(task.endsWith(RoutineTemplates.DRAFT_ONLY_RULE))
    }

    @Test
    fun `bad times are rejected`() {
        var failed = false
        try {
            validateTemplateTime(ScheduleKind.DAILY, hour = 25, minute = 0, dayOfWeek = 1)
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `notification route opens the routine chat`() {
        assertEquals("chat_room/12?enabled=platform-1", RoutineChatLaunch.route(12, "platform-1"))
    }

    private fun sampleRoutine(chatId: Int) = RoutineEntity(
        routineUid = RoutineTemplates.MORNING_ID,
        title = "Morning briefing",
        prompt = "old",
        botUid = null,
        platformUid = "platform-1",
        scheduleKind = ScheduleKind.DAILY.name,
        cronExpression = null,
        hour = 7,
        minute = 30,
        dayOfWeek = 1,
        pluginUids = "",
        enabled = false,
        chatId = chatId,
        createdAt = 10,
        updatedAt = 10
    )

    private fun plugin(uid: String, catalogId: String, enabled: Boolean) = PluginEntity(
        pluginUid = uid,
        catalogId = catalogId,
        name = catalogId,
        endpointUrl = "https://example.test/mcp",
        transport = "STREAMABLE_HTTP",
        authType = "OAUTH",
        secretRef = null,
        oauthClientId = null,
        allowCleartext = false,
        enabled = enabled,
        status = "CONNECTED",
        statusDetail = null,
        createdAt = 0,
        updatedAt = 0
    )
}
