package dev.chungjungsoo.gptmobile.data.project

import androidx.compose.runtime.Immutable
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.Project

sealed class ProjectFilter {
    data object All : ProjectFilter()
    data object Ungrouped : ProjectFilter()
    data class Project(val id: Int) : ProjectFilter()
}

sealed class ChatListRow {
    @Immutable
    data class Header(val key: String, val title: String, val projectId: Int?) : ChatListRow()

    @Immutable
    data class Chat(val chat: ChatRoomV2, val chatIndex: Int) : ChatListRow()
}

fun chatListRowKey(row: ChatListRow): String = when (row) {
    is ChatListRow.Header -> "header-${row.key}"
    is ChatListRow.Chat -> if (row.chat.id > 0) {
        "chat-${row.chat.id}"
    } else {
        "chat-new-${row.chat.createdAt}-${row.chatIndex}"
    }
}

fun groupChatsByProject(
    chats: List<ChatRoomV2>,
    projects: List<Project>,
    filter: ProjectFilter,
    ungroupedTitle: String,
    grouped: Boolean = true
): List<ChatListRow> {
    val visible = chats.mapIndexed { index, chat -> index to chat }.filter { (_, chat) ->
        when (filter) {
            ProjectFilter.All -> true
            ProjectFilter.Ungrouped -> chat.projectId == null
            is ProjectFilter.Project -> chat.projectId == filter.id
        }
    }
    if (!grouped || filter != ProjectFilter.All || projects.isEmpty()) {
        return visible.map { (index, chat) -> ChatListRow.Chat(chat, index) }
    }
    val names = projects.associate { it.id to it.name }
    val rows = mutableListOf<ChatListRow>()
    projects.forEach { project ->
        val members = visible.filter { it.second.projectId == project.id }
        if (members.isEmpty()) return@forEach
        rows += ChatListRow.Header("project-${project.id}", project.name, project.id)
        members.forEach { (index, chat) -> rows += ChatListRow.Chat(chat, index) }
    }
    val loose = visible.filter { (_, chat) -> chat.projectId == null || chat.projectId !in names }
    if (loose.isNotEmpty()) {
        if (rows.isNotEmpty()) {
            rows += ChatListRow.Header("none", ungroupedTitle, null)
        }
        loose.forEach { (index, chat) -> rows += ChatListRow.Chat(chat, index) }
    }
    return rows
}
