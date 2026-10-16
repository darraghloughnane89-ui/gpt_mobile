package dev.chungjungsoo.gptmobile.data.plugin

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DelegateTaskSecurityTest {
    @Test
    fun unattendedHandoffDoesNotSend() = runBlocking {
        var sent = false
        val hermes = HermesBridge {
            sent = true
            "sent"
        }
        val targets = object : AgentTargetSource {
            override suspend fun list(): List<AgentTargetRef> = emptyList()

            override suspend fun accepts(targetId: String): Boolean = false
        }
        val tool = delegateTool(
            AgentHandoffExecutor(targets, AgentReplySource { _, _ -> "no" }, hermes),
            targets
        )
        val result = withContext(UnattendedToolCall()) {
            tool.execute(
                "call",
                buildJsonObject {
                    put("target_id", JsonPrimitive("hermes"))
                    put("task", JsonPrimitive("send the inbox"))
                }
            )
        }

        assertTrue(result.isError)
        assertFalse(sent)
    }

    @Test
    fun unconfirmedHandoffDoesNotSend() = runBlocking {
        var sent = false
        val hermes = HermesBridge {
            sent = true
            "sent"
        }
        val targets = object : AgentTargetSource {
            override suspend fun list(): List<AgentTargetRef> = emptyList()

            override suspend fun accepts(targetId: String): Boolean = false
        }
        val tool = delegateTool(
            AgentHandoffExecutor(targets, AgentReplySource { _, _ -> "no" }, hermes),
            targets,
            ToolConfirmPrompter { false }
        )
        val result = tool.execute(
            "call",
            buildJsonObject {
                put("target_id", JsonPrimitive("hermes"))
                put("task", JsonPrimitive("send the inbox"))
            }
        )

        assertTrue(result.isError)
        assertFalse(sent)
    }
}
