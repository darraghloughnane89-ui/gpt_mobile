package dev.chungjungsoo.gptmobile.data.context

import dev.chungjungsoo.gptmobile.data.database.entity.ACTIVE_REVISION_LATEST
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import kotlin.comparisons.compareBy

data class BranchSwitchState(
    val position: Int,
    val total: Int
)

data class BranchedTranscript(
    val activeUsers: List<MessageV2>,
    val activeAssistants: List<List<MessageV2>>,
    val archived: List<MessageV2>
)

data class UserEditBranch(
    val messages: List<MessageV2>,
    val newUserMessage: MessageV2
)

fun branchSwitcherLabel(position: Int, total: Int): String = "< $position/$total >"

fun assistantRevisionSwitch(message: MessageV2): BranchSwitchState? {
    if (message.platformType == null || message.revisions.isEmpty()) return null
    val total = message.revisions.size + 1
    val position = if (message.activeRevisionIndex == ACTIVE_REVISION_LATEST) {
        total
    } else {
        (message.revisions.size - message.activeRevisionIndex).coerceIn(1, total)
    }
    return BranchSwitchState(position = position, total = total)
}

fun userBranchSwitch(message: MessageV2, messages: List<MessageV2>): BranchSwitchState? {
    if (message.platformType != null || !hasConversationBranches(messages)) return null
    val siblings = siblingUsers(messages, message.parentMessageId)
    if (siblings.size < 2) return null
    val index = siblings.indexOfFirst { it.id == message.id && it.siblingIndex == message.siblingIndex }
    if (index < 0) return null
    return BranchSwitchState(position = index + 1, total = siblings.size)
}

fun activePathMessageIds(
    userMessages: List<MessageV2>,
    assistantMessages: List<List<MessageV2>>,
    turnIndex: Int,
    includeAssistantTurn: Boolean
): List<Int> {
    if (turnIndex !in userMessages.indices) return emptyList()
    val users = userMessages.take(turnIndex + 1)
    val assistantTurnCount = if (includeAssistantTurn) turnIndex + 1 else turnIndex
    val assistants = assistantMessages.take(assistantTurnCount).flatten()
    return (users + assistants).map { it.id }.filter { it > 0 }.distinct()
}

fun branchedChatTitle(title: String): String = "$title branch".replace('\n', ' ').take(50)

fun activeTranscript(messages: List<MessageV2>): BranchedTranscript {
    if (!hasConversationBranches(messages)) {
        val (users, assistants) = groupPersistedConversation(messages)
        return BranchedTranscript(
            activeUsers = users,
            activeAssistants = assistants,
            archived = emptyList()
        )
    }

    val path = activeUserPath(messages)
    val activeAssistantRows = path.map { user -> assistantsLinkedTo(messages, user.id) }
    val activeUserIds = path.map { it.id }.filter { it > 0 }.toSet()
    val activeAssistantIds = activeAssistantRows.flatten().map { it.id }.filter { it > 0 }.toSet()
    val archived = messages.filter { message ->
        when {
            message.id <= 0 -> message !in path
            message.platformType == null -> message.id !in activeUserIds
            else -> message.id !in activeAssistantIds
        }
    }
    return BranchedTranscript(
        activeUsers = path,
        activeAssistants = activeAssistantRows,
        archived = archived
    )
}

fun applyUserEditBranch(
    messages: List<MessageV2>,
    editedUserId: Int,
    content: String,
    attachments: List<ChatAttachment>,
    timestamp: Long
): UserEditBranch? {
    val edited = messages.firstOrNull { it.id == editedUserId && it.platformType == null } ?: return null
    if (edited.id <= 0) return null
    if (messages.any { it.platformType == null && it.id <= 0 }) return null

    val linked = linkLegacyConversation(messages)
    val editedLinked = linked.first { it.id == editedUserId && it.platformType == null }
    val siblings = siblingUsers(linked, editedLinked.parentMessageId)
    val nextSiblingIndex = (siblings.maxOfOrNull { it.siblingIndex } ?: -1) + 1
    val updated = linked.map { message ->
        if (message.platformType == null && message.parentMessageId == editedLinked.parentMessageId) {
            message.copy(isActiveSibling = false)
        } else {
            message
        }
    }
    val newUserMessage = editedLinked.copy(
        id = 0,
        content = content,
        attachments = attachments,
        thoughts = "",
        revisions = emptyList(),
        activeRevisionIndex = ACTIVE_REVISION_LATEST,
        linkedMessageId = 0,
        platformType = null,
        currentRunId = null,
        timeline = emptyList(),
        siblingIndex = nextSiblingIndex,
        isActiveSibling = true,
        createdAt = timestamp
    )
    return UserEditBranch(
        messages = updated + newUserMessage,
        newUserMessage = newUserMessage
    )
}

fun selectActiveSibling(
    messages: List<MessageV2>,
    parentMessageId: Int,
    siblingIndex: Int
): List<MessageV2> {
    val siblings = siblingUsers(messages, parentMessageId)
    if (siblings.none { it.siblingIndex == siblingIndex }) return messages
    return messages.map { message ->
        if (message.platformType != null || message.parentMessageId != parentMessageId) {
            message
        } else {
            message.copy(isActiveSibling = message.siblingIndex == siblingIndex)
        }
    }
}

internal fun hasConversationBranches(messages: List<MessageV2>): Boolean {
    val users = messages.filter { it.platformType == null }
    if (users.any { !it.isActiveSibling || it.siblingIndex != 0 }) return true
    val hasExplicitParent = users.any { it.parentMessageId != 0 || it.siblingIndex != 0 }
    if (users.count { it.parentMessageId == 0 } > 1 && hasExplicitParent) return true
    return users.groupBy { it.parentMessageId }.any { (parentId, siblings) ->
        parentId != 0 && siblings.size > 1
    }
}

internal fun linkLegacyConversation(messages: List<MessageV2>): List<MessageV2> {
    val users = messages.filter { it.platformType == null }
    if (users.any { it.parentMessageId != 0 || it.siblingIndex != 0 || !it.isActiveSibling }) {
        return messages
    }
    if (users.any { it.id <= 0 }) return messages

    val (orderedUsers, assistantRows) = groupPersistedConversation(messages)
    val parentById = orderedUsers.mapIndexed { index, message ->
        message.id to if (index == 0) 0 else orderedUsers[index - 1].id
    }.toMap()
    val repairedLinks = buildMap {
        orderedUsers.forEachIndexed { index, user ->
            assistantRows.getOrNull(index).orEmpty().forEach { assistant ->
                if (assistant.id > 0 && assistant.linkedMessageId <= 0) {
                    put(assistant.id, user.id)
                }
            }
        }
    }
    return messages.map { message ->
        if (message.platformType == null) {
            message.copy(
                parentMessageId = parentById[message.id] ?: 0,
                siblingIndex = 0,
                isActiveSibling = true
            )
        } else {
            val repairedLink = repairedLinks[message.id]
            if (repairedLink == null) message else message.copy(linkedMessageId = repairedLink)
        }
    }
}

private fun activeUserPath(messages: List<MessageV2>): List<MessageV2> {
    val users = messages.filter { it.platformType == null }
    val children = users.groupBy { it.parentMessageId }
    val path = mutableListOf<MessageV2>()
    var parentId = 0
    val visited = mutableSetOf<Int>()
    while (path.size < users.size) {
        val siblings = children[parentId].orEmpty().sortedWith(siblingOrder)
        if (siblings.isEmpty()) break
        val active = siblings.firstOrNull { it.isActiveSibling } ?: siblings.last()
        if (active.id > 0 && !visited.add(active.id)) break
        path += active
        if (active.id <= 0) break
        parentId = active.id
    }
    return path
}

private fun assistantsLinkedTo(messages: List<MessageV2>, userId: Int): List<MessageV2> {
    if (userId <= 0) return emptyList()
    return messages
        .filter { it.platformType != null && it.linkedMessageId == userId }
        .sortedBy { it.id }
}

private fun siblingUsers(messages: List<MessageV2>, parentMessageId: Int): List<MessageV2> = messages
    .filter { it.platformType == null && it.parentMessageId == parentMessageId }
    .sortedWith(siblingOrder)

private val siblingOrder = compareBy<MessageV2>({ it.siblingIndex }, { it.id })
