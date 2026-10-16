package dev.chungjungsoo.gptmobile.presentation.ui.chat

import dev.chungjungsoo.gptmobile.data.agent.LiveAgentResponseSnapshot
import dev.chungjungsoo.gptmobile.data.database.entity.AssistantTimelineItem
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.hasHistoricalRevisionSelected

internal fun nextLiveHighWater(
    previous: Map<String, LiveAgentResponseSnapshot>,
    incoming: Map<String, LiveAgentResponseSnapshot>,
    persisted: ChatViewModel.GroupedMessages
): Map<String, LiveAgentResponseSnapshot> {
    if (previous.isEmpty() && incoming.isEmpty()) return incoming

    val merged = LinkedHashMap(previous)
    incoming.forEach { (runId, snapshot) ->
        val current = merged[runId]
        merged[runId] = if (current == null) snapshot else longerSnapshot(current, snapshot)
    }
    val persistedByRunId = HashMap<String, MessageV2>(merged.size)
    if (merged.isNotEmpty()) {
        for (row in persisted.assistantMessages) {
            for (message in row) {
                val runId = message.currentRunId ?: continue
                if (runId in merged) persistedByRunId[runId] = message
            }
        }
    }
    return merged.filter { (runId, snapshot) ->
        val row = persistedByRunId[runId]
        row == null || snapshotAheadOf(snapshot, row)
    }
}

internal fun overlayLiveSnapshots(
    persisted: ChatViewModel.GroupedMessages,
    liveByRunId: Map<String, LiveAgentResponseSnapshot>
): ChatViewModel.GroupedMessages {
    if (liveByRunId.isEmpty()) return persisted
    var changed = false
    val rows = persisted.assistantMessages.map { row ->
        if (row.none { message -> messageNeedsLiveOverlay(message, liveByRunId) }) {
            row
        } else {
            changed = true
            row.map { message -> overlayLiveMessage(message, liveByRunId) }
        }
    }
    if (!changed) return persisted
    return persisted.copy(assistantMessages = rows)
}

private fun messageNeedsLiveOverlay(
    message: MessageV2,
    liveByRunId: Map<String, LiveAgentResponseSnapshot>
): Boolean {
    if (message.hasHistoricalRevisionSelected()) return false
    if (message.currentRunId?.let(liveByRunId::get) != null) return true
    return message.id > 0 && liveByRunId.values.any { snapshot -> snapshot.messageId == message.id }
}

private fun overlayLiveMessage(
    message: MessageV2,
    liveByRunId: Map<String, LiveAgentResponseSnapshot>
): MessageV2 {
    if (message.hasHistoricalRevisionSelected()) return message
    val live = message.currentRunId?.let(liveByRunId::get)
        ?: liveByRunId.values.firstOrNull { snapshot -> snapshot.messageId == message.id && message.id > 0 }
        ?: return message
    return message.copy(
        content = longerText(message.content, live.content),
        thoughts = longerText(message.thoughts, live.thoughts),
        timeline = longerTimeline(message.timeline, live.timeline)
    )
}

internal fun longerSnapshot(
    first: LiveAgentResponseSnapshot,
    second: LiveAgentResponseSnapshot
): LiveAgentResponseSnapshot = if (snapshotLength(second) > snapshotLength(first)) second else first

internal fun snapshotAheadOf(snapshot: LiveAgentResponseSnapshot, row: MessageV2): Boolean = snapshot.content.length > row.content.length ||
    snapshot.thoughts.length > row.thoughts.length ||
    timelineLength(snapshot.timeline) > timelineLength(row.timeline)

private fun snapshotLength(snapshot: LiveAgentResponseSnapshot): Int = snapshot.content.length + snapshot.thoughts.length + timelineLength(snapshot.timeline)

private fun longerText(current: String, live: String): String = if (live.length >= current.length) live else current

private fun longerTimeline(
    current: List<AssistantTimelineItem>,
    live: List<AssistantTimelineItem>
): List<AssistantTimelineItem> = if (timelineLength(live) >= timelineLength(current)) live else current

private fun timelineLength(items: List<AssistantTimelineItem>): Int = items.sumOf { it.content.length } + items.size
