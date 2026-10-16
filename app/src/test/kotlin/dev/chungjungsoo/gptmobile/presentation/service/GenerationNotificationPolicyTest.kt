package dev.chungjungsoo.gptmobile.presentation.service

import dev.chungjungsoo.gptmobile.data.agent.ActiveAgentRun
import dev.chungjungsoo.gptmobile.data.agent.LiveAgentResponseSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationNotificationPolicyTest {
    @Test
    fun `stop still uses the existing run cancellation actions`() {
        assertTrue(isGenerationStopAction(ACTION_STOP_GENERATION))
        assertTrue(isGenerationStopAction(ACTION_CANCEL_AGENT_RUNS))
        assertFalse(isGenerationStopAction(ACTION_REPLY_TO_CHAT))
        assertFalse(isGenerationStopAction(null))
    }

    @Test
    fun `notification permission and a blocked channel suppress delivery`() {
        assertEquals(
            NotificationDelivery.SUPPRESSED,
            notificationDelivery(
                sdkInt = 33,
                permissionGranted = false,
                appNotificationsEnabled = true,
                channel = ChannelBlockState.OPEN
            )
        )
        assertEquals(
            NotificationDelivery.SUPPRESSED,
            notificationDelivery(
                sdkInt = 34,
                permissionGranted = true,
                appNotificationsEnabled = false,
                channel = ChannelBlockState.OPEN
            )
        )
        assertEquals(
            NotificationDelivery.SUPPRESSED,
            notificationDelivery(
                sdkInt = 31,
                permissionGranted = false,
                appNotificationsEnabled = true,
                channel = ChannelBlockState.BLOCKED
            )
        )
        assertEquals(
            NotificationDelivery.DELIVER,
            notificationDelivery(
                sdkInt = 31,
                permissionGranted = false,
                appNotificationsEnabled = true,
                channel = ChannelBlockState.ABSENT
            )
        )
        assertEquals(
            NotificationDelivery.DELIVER,
            notificationDelivery(
                sdkInt = 34,
                permissionGranted = true,
                appNotificationsEnabled = true,
                channel = ChannelBlockState.OPEN
            )
        )
        assertFalse(shouldPostCompletionNotification(NotificationDelivery.SUPPRESSED))
        assertTrue(shouldPostCompletionNotification(NotificationDelivery.DELIVER))
    }

    @Test
    fun `progress preview follows received text and refreshes by chunk`() {
        val progress = generationProgress(
            runs = listOf(
                ActiveAgentRun("run-a", 4, "openai"),
                ActiveAgentRun("run-b", 5, "anthropic")
            ),
            snapshots = mapOf(
                "run-a" to LiveAgentResponseSnapshot("run-a", 1, "Hello from the model", "", emptyList()),
                "run-b" to LiveAgentResponseSnapshot("run-b", 2, "", "thinking", emptyList())
            )
        )

        assertEquals(2, progress.activeCount)
        assertEquals(setOf(4, 5), progress.chatIds)
        assertEquals("Hello from the model", progress.preview)
        assertEquals("Hello from the model".length + "thinking".length, progress.receivedCharacters)
        assertEquals("Hello from the model", progress.previewByChat[4])
        assertEquals("thinking", progress.previewByChat[5])

        val longText = "word ".repeat(40)
        assertTrue(streamingPreview(longText).startsWith("…"))
        assertTrue(streamingPreview(longText).length <= 140)
        assertEquals("a b", streamingPreview(" a\n\tb "))

        assertTrue(shouldRefreshProgressNotification(-1, 10, 0, 1))
        assertFalse(shouldRefreshProgressNotification(10, 20, 1, 1))
        assertTrue(shouldRefreshProgressNotification(10, 70, 1, 1))
        assertTrue(shouldRefreshProgressNotification(70, 70, 1, 2))
    }

    @Test
    fun `completion opens one chat and uses a distinct notification id`() {
        assertEquals(listOf(3, 9), completionChatIds(setOf(9, 0, 3, 3)))
        assertEquals("chat_room/9?enabled=alpha,beta", chatNotificationRoute(9, listOf("alpha", "beta")))
        assertNull(chatNotificationRoute(0, listOf("alpha")))
        assertNull(chatNotificationRoute(9, listOf(" ")))
        assertTrue(completionNotificationId(9) != ONGOING_NOTIFICATION_ID)
        assertEquals("Follow up", normalizedReplyText("  Follow up  "))
        assertNull(normalizedReplyText("   "))
    }
}
