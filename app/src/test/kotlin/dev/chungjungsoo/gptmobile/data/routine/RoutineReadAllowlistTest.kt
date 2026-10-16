package dev.chungjungsoo.gptmobile.data.routine

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import dev.chungjungsoo.gptmobile.data.agent.tool.ResolvedAgentTool
import dev.chungjungsoo.gptmobile.data.database.entity.RoutineEntity
import dev.chungjungsoo.gptmobile.data.plugin.AllowToolConfirmPrompter
import dev.chungjungsoo.gptmobile.data.plugin.GatedAgentTool
import dev.chungjungsoo.gptmobile.data.plugin.RoutineReadAllowlist
import dev.chungjungsoo.gptmobile.data.plugin.ToolEffect
import dev.chungjungsoo.gptmobile.data.plugin.UnattendedToolCall
import dev.chungjungsoo.gptmobile.data.plugin.parseRoutineReadAllowlist
import dev.chungjungsoo.gptmobile.data.plugin.visibleRoutineTools
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineReadAllowlistTest {
    @Test
    fun newRoutine_allowlistDefaultsToNone() {
        val routine = RoutineEntity(
            routineUid = "routine",
            title = "Morning",
            prompt = "Summarize the inbox",
            botUid = null,
            platformUid = "platform",
            scheduleKind = "DAILY",
            cronExpression = null,
            hour = 8,
            minute = 0,
            dayOfWeek = 1,
            pluginUids = "gmail",
            enabled = true,
            chatId = null,
            createdAt = 0,
            updatedAt = 0
        )
        assertEquals("", routine.allowedReadTools)
    }

    @Test
    fun parse_blankAllowlist_isEmpty() {
        assertEquals(emptySet<String>(), parseRoutineReadAllowlist(null))
        assertEquals(emptySet<String>(), parseRoutineReadAllowlist("  "))
        assertEquals(setOf("list_messages", "get_thread"), parseRoutineReadAllowlist("list_messages, get_thread"))
    }

    @Test
    fun unattendedRun_allowsOnlyNamedReadTools_andNeverSends() = runBlocking {
        var listedRan = false
        var otherRan = false
        var sendRan = false
        val allowlist = RoutineReadAllowlist(setOf("list_messages", "send_mail"))
        val listed = withContext(UnattendedToolCall() + allowlist) {
            tool(ToolEffect.READ, "list_messages") { listedRan = true }.execute("1", JsonObject(emptyMap()))
        }
        val other = withContext(UnattendedToolCall() + allowlist) {
            tool(ToolEffect.READ, "get_thread") { otherRan = true }.execute("2", JsonObject(emptyMap()))
        }
        val send = withContext(UnattendedToolCall() + allowlist) {
            tool(ToolEffect.WRITE, "send_mail") { sendRan = true }.execute("3", JsonObject(emptyMap()))
        }
        var defaultRan = false
        val defaultNone = withContext(UnattendedToolCall()) {
            tool(ToolEffect.READ, "list_messages") { defaultRan = true }.execute("4", JsonObject(emptyMap()))
        }

        assertFalse(listed.isError)
        assertTrue(listedRan)
        assertTrue(other.isError)
        assertFalse(otherRan)
        assertTrue(send.isError)
        assertFalse(sendRan)
        assertTrue((send.content as ToolResultContent.Text).text.contains("draft", ignoreCase = true))
        assertTrue(defaultNone.isError)
        assertFalse(defaultRan)
    }

    @Test
    fun visibleTools_keepAllowlistedReadsOnly() {
        val tools = listOf(
            resolved("list_messages", ToolEffect.READ),
            resolved("get_thread", ToolEffect.READ),
            resolved("send_mail", ToolEffect.WRITE),
            resolved("delegate_task", ToolEffect.WRITE, gated = false)
        )
        val visible = visibleRoutineTools(tools, setOf("list_messages", "send_mail"))
        assertEquals(listOf("list_messages"), visible.map { it.modelToolName })
        assertEquals(emptyList<String>(), visibleRoutineTools(tools, emptySet()).map { it.modelToolName })
    }

    private fun tool(effect: ToolEffect, name: String, ran: () -> Unit = {}): GatedAgentTool {
        val delegate = object : AgentTool {
            override val definition = AgentToolDefinition(name, name, buildJsonObject { })
            override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
                ran()
                return AgentToolResult(callId, ToolResultContent.Text("ok"), isError = false)
            }
        }
        return GatedAgentTool(
            definition = delegate.definition,
            effect = effect,
            enabled = true,
            pluginName = "Gmail",
            prompter = AllowToolConfirmPrompter,
            delegate = delegate
        )
    }

    private fun resolved(name: String, effect: ToolEffect, gated: Boolean = true): ResolvedAgentTool {
        val agent = if (gated) {
            tool(effect, name)
        } else {
            object : AgentTool {
                override val definition = AgentToolDefinition(name, name, buildJsonObject { })
                override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult =
                    AgentToolResult(callId, ToolResultContent.Text("sent"), isError = false)
            }
        }
        return ResolvedAgentTool(agent, "plugin", "Gmail", name, name)
    }
}
