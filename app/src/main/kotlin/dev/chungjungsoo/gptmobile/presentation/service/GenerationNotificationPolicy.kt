package dev.chungjungsoo.gptmobile.presentation.service

import dev.chungjungsoo.gptmobile.data.agent.ActiveAgentRun
import dev.chungjungsoo.gptmobile.data.agent.LiveAgentResponseSnapshot
import dev.chungjungsoo.gptmobile.presentation.common.Route

internal const val GENERATION_PROGRESS_CHANNEL_ID = "agent_runs"
internal const val GENERATION_COMPLETE_CHANNEL_ID = "generation_complete"
internal const val ONGOING_NOTIFICATION_ID = 8001
internal const val COMPLETION_NOTIFICATION_BASE = 20_000
internal const val ACTION_STOP_GENERATION = "dev.chungjungsoo.gptmobile.action.STOP_GENERATION"
internal const val ACTION_CANCEL_AGENT_RUNS = "dev.chungjungsoo.gptmobile.action.CANCEL_AGENT_RUNS"
internal const val ACTION_REPLY_TO_CHAT = "dev.chungjungsoo.gptmobile.action.REPLY_TO_CHAT"
internal const val EXTRA_OPEN_CHAT_ID = "dev.chungjungsoo.gptmobile.extra.OPEN_CHAT_ID"
internal const val EXTRA_REPLY_TEXT = "dev.chungjungsoo.gptmobile.extra.REPLY_TEXT"

private val COLLAPSED_WHITESPACE = Regex("\\s+")

internal enum class ChannelBlockState {
    ABSENT,
    OPEN,
    BLOCKED
}

internal enum class NotificationDelivery {
    DELIVER,
    SUPPRESSED
}

internal data class GenerationProgress(
    val activeCount: Int,
    val chatIds: Set<Int>,
    val preview: String,
    val receivedCharacters: Int,
    val previewByChat: Map<Int, String>
)

internal fun isGenerationStopAction(action: String?): Boolean = action == ACTION_STOP_GENERATION || action == ACTION_CANCEL_AGENT_RUNS

internal fun notificationsPermitted(sdkInt: Int, permissionGranted: Boolean): Boolean = sdkInt < 33 || permissionGranted

/**
 * App notification permission and the system per-channel block state.
 * [ChannelBlockState.ABSENT] still delivers so a newly created channel can post.
 */
internal fun notificationDelivery(
    sdkInt: Int,
    permissionGranted: Boolean,
    appNotificationsEnabled: Boolean,
    channel: ChannelBlockState
): NotificationDelivery {
    if (!notificationsPermitted(sdkInt, permissionGranted) || !appNotificationsEnabled) {
        return NotificationDelivery.SUPPRESSED
    }
    if (channel == ChannelBlockState.BLOCKED) return NotificationDelivery.SUPPRESSED
    return NotificationDelivery.DELIVER
}

internal fun shouldPostCompletionNotification(delivery: NotificationDelivery): Boolean = delivery == NotificationDelivery.DELIVER

internal fun streamingPreview(text: String, maxChars: Int = 140): String {
    val collapsed = text.replace(COLLAPSED_WHITESPACE, " ").trim()
    if (collapsed.length <= maxChars) return collapsed
    if (maxChars <= 1) return "…"
    return "…" + collapsed.takeLast(maxChars - 1)
}

internal fun generationProgress(
    runs: Collection<ActiveAgentRun>,
    snapshots: Map<String, LiveAgentResponseSnapshot>
): GenerationProgress {
    val active = runs.distinctBy(ActiveAgentRun::runId)
    val previews = linkedMapOf<Int, String>()
    var receivedCharacters = 0
    active.forEach { run ->
        val snapshot = snapshots[run.runId]
        val content = snapshot?.content.orEmpty()
        val thoughts = snapshot?.thoughts.orEmpty()
        receivedCharacters += content.length + thoughts.length
        val visible = content.ifBlank { thoughts }
        if (visible.isBlank()) return@forEach
        val preview = streamingPreview(visible)
        val current = previews[run.chatId]
        if (current == null || preview.length >= current.length) {
            previews[run.chatId] = preview
        }
    }
    val preview = previews.values.maxByOrNull { it.length }.orEmpty()
    return GenerationProgress(
        activeCount = active.size,
        chatIds = active.mapTo(mutableSetOf(), ActiveAgentRun::chatId),
        preview = preview,
        receivedCharacters = receivedCharacters,
        previewByChat = previews
    )
}

internal fun shouldRefreshProgressNotification(
    previousCharacters: Int,
    nextCharacters: Int,
    previousCount: Int,
    nextCount: Int,
    bucketSize: Int = 64
): Boolean {
    if (nextCount != previousCount) return true
    if (nextCharacters == previousCharacters) return false
    if (previousCharacters < 0) return true
    return nextCharacters / bucketSize != previousCharacters / bucketSize
}

internal fun completionChatIds(chatIds: Set<Int>): List<Int> = chatIds.filter { it > 0 }.distinct().sorted()

internal fun completionNotificationId(chatId: Int): Int {
    val id = COMPLETION_NOTIFICATION_BASE + chatId
    return if (id == ONGOING_NOTIFICATION_ID) ONGOING_NOTIFICATION_ID + 1 else id
}

internal fun chatNotificationRoute(chatId: Int, enabledPlatforms: List<String>): String? {
    if (chatId <= 0) return null
    val platforms = enabledPlatforms.filter { it.isNotBlank() }
    if (platforms.isEmpty()) return null
    return Route.CHAT_ROOM
        .replace("{chatRoomId}", chatId.toString())
        .replace("{enabledPlatforms}", platforms.joinToString(","))
}

internal fun normalizedReplyText(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
