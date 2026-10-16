package dev.chungjungsoo.gptmobile.data.plugin

import dev.chungjungsoo.gptmobile.data.agent.AgentTool
import dev.chungjungsoo.gptmobile.data.agent.AgentToolDefinition
import dev.chungjungsoo.gptmobile.data.agent.AgentToolResult
import dev.chungjungsoo.gptmobile.data.agent.ToolResultContent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolConfirmationGateTest {
    @Test
    fun `hints win and unknown names stay write gated`() {
        assertEquals(ToolEffect.READ, classifyToolEffect("send_mail", "Send", readOnlyHint = true))
        assertEquals(ToolEffect.WRITE, classifyToolEffect("list_files", "List", destructiveHint = true))
        assertEquals(ToolEffect.READ, classifyToolEffect("list_messages", "List mail"))
        assertEquals(ToolEffect.WRITE, classifyToolEffect("create_event", "Create a calendar event"))
        assertEquals(ToolEffect.WRITE, classifyToolEffect("mystery", "Does something"))
        assertEquals(ToolEffect.READ, classifyToolEffect("status_check", "Current status"))
    }

    @Test
    fun `together does not match get`() {
        assertEquals(ToolEffect.WRITE, classifyToolEffect("together", "nothing useful"))
    }

    @Test
    fun `write tools wait for confirmation and unattended calls are denied`() = runBlocking {
        var prompts = 0
        val denied = gated(ToolEffect.WRITE, enabled = true) {
            prompts += 1
            false
        }
        val deniedResult = denied.execute("call-1", buildJsonObject { })
        assertTrue(deniedResult.isError)
        assertEquals(1, prompts)

        val allowed = gated(ToolEffect.WRITE, enabled = true) { true }
        assertFalse(allowed.execute("call-2", JsonObject(emptyMap())).isError)

        val unattended = gated(ToolEffect.WRITE, enabled = true, AllowToolConfirmPrompter)
        val unattendedResult = withContext(UnattendedToolCall()) {
            unattended.execute("call-3", JsonObject(emptyMap()))
        }
        assertTrue(unattendedResult.isError)
    }

    @Test
    fun `read tools run without a prompt and disabled tools never run`() = runBlocking {
        var ran = false
        var prompted = false
        val read = gated(ToolEffect.READ, enabled = true, ran = { ran = true }) {
            prompted = true
            false
        }
        assertFalse(read.execute("call-4", JsonObject(emptyMap())).isError)
        assertTrue(ran)
        assertFalse(prompted)

        ran = false
        val disabled = gated(ToolEffect.READ, enabled = false, ran = { ran = true })
        assertTrue(disabled.execute("call-5", JsonObject(emptyMap())).isError)
        assertFalse(ran)
    }

    private fun gated(
        effect: ToolEffect,
        enabled: Boolean,
        prompter: ToolConfirmPrompter = AllowToolConfirmPrompter,
        ran: () -> Unit = {},
        confirm: (suspend (ToolConfirmRequest) -> Boolean)? = null
    ): GatedAgentTool {
        val delegate = object : AgentTool {
            override val definition = AgentToolDefinition("tool", "Tool", buildJsonObject { })
            override suspend fun execute(callId: String, arguments: JsonObject): AgentToolResult {
                ran()
                return AgentToolResult(callId, ToolResultContent.Text("ok"), isError = false)
            }
        }
        val actual = confirm?.let { body ->
            ToolConfirmPrompter { request -> body(request) }
        } ?: prompter
        return GatedAgentTool(
            definition = delegate.definition,
            effect = effect,
            enabled = enabled,
            pluginName = "Gmail",
            prompter = actual,
            delegate = delegate
        )
    }
}
