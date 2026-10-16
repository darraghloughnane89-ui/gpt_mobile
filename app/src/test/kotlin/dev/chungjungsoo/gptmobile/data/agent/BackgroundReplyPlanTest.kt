package dev.chungjungsoo.gptmobile.data.agent

import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.Bot
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PersistAgentTurnResult
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundReplyPlanTest {
    @Test
    fun `reply keeps history chat model and bot instructions`() {
        val plan = planBackgroundReply(
            chat = ChatRoomV2(
                id = 4,
                title = "Chat",
                enabledPlatform = listOf("openai"),
                botUid = "bot"
            ),
            history = listOf(
                MessageV2(id = 1, chatId = 4, content = "Hi", platformType = null),
                MessageV2(id = 2, chatId = 4, content = "Hello", platformType = "openai", linkedMessageId = 1)
            ),
            platforms = listOf(platform(enabled = true)),
            chatPlatformModels = mapOf("openai" to "chat-model"),
            bot = Bot(botUid = "bot", name = "Robin", instructions = "Be brief"),
            replyText = " Next ",
            nowSeconds = 10L,
            runIds = { "run-1" }
        )

        requireNotNull(plan)
        assertEquals("Next", plan.persist.userMessage.content)
        assertEquals(10L, plan.persist.chatRoom.updatedAt)
        assertEquals("chat-model", plan.platforms.single().model)
        assertEquals("chat-model", plan.persist.runs.single().modelSnapshot)
        assertEquals("Be brief", plan.completion.botInstructions)
        assertEquals("bot", plan.completion.botUid)
        assertEquals(listOf("Hi"), plan.priorUsers.map(MessageV2::content))
        assertEquals("Hello", plan.priorAssistants.single().single().content)

        val persistedAssistant = MessageV2(id = 8, chatId = 4, content = "", platformType = "openai")
        val requests = requestsForPersistedReply(
            plan,
            PersistAgentTurnResult(
                chatRoom = plan.persist.chatRoom,
                userMessage = plan.persist.userMessage.copy(id = 7),
                assistantMessages = listOf(persistedAssistant),
                runs = listOf(
                    AgentRun(
                        runId = "run-1",
                        chatId = 4,
                        userMessageId = 7,
                        assistantMessageId = 8,
                        profileUid = "openai",
                        providerSnapshot = "OPENAI",
                        modelSnapshot = "chat-model"
                    )
                )
            )
        )

        assertEquals(listOf("Hi", "Next"), requests.single().userMessages.map(MessageV2::content))
        assertEquals(persistedAssistant, requests.single().assistantMessages.last().single())
        assertEquals("Hello", requests.single().assistantMessages.first().single().content)
        assertEquals("run-1", requests.single().runId)
    }

    @Test
    fun `blank text and a disabled platform do not start a reply`() {
        val chat = ChatRoomV2(id = 4, title = "Chat", enabledPlatform = listOf("openai"))
        assertNull(
            planBackgroundReply(
                chat = chat,
                history = emptyList(),
                platforms = listOf(platform(enabled = true)),
                chatPlatformModels = emptyMap(),
                bot = null,
                replyText = "  ",
                nowSeconds = 1L
            )
        )
        assertNull(
            planBackgroundReply(
                chat = chat,
                history = emptyList(),
                platforms = listOf(platform(enabled = false)),
                chatPlatformModels = emptyMap(),
                bot = null,
                replyText = "Hello",
                nowSeconds = 1L
            )
        )
    }

    private fun platform(enabled: Boolean) = PlatformV2(
        uid = "openai",
        name = "OpenAI",
        compatibleType = ClientType.OPENAI,
        enabled = enabled,
        apiUrl = "https://api.openai.com/v1/",
        model = "default-model"
    )
}
