package dev.chungjungsoo.gptmobile.data.library

import kotlinx.serialization.Serializable

const val MAX_FOLDER_NAME_LENGTH = 40

@Serializable
data class ChatFolder(
    val id: String,
    val name: String
)

@Serializable
data class ChatLibraryState(
    val pinnedChatIds: Set<Int> = emptySet(),
    val archivedChatIds: Set<Int> = emptySet(),
    val temporaryChatIds: Set<Int> = emptySet(),
    val folders: List<ChatFolder> = emptyList(),
    val chatFolderIds: Map<Int, String> = emptyMap()
)

enum class FolderNameIssue {
    Blank,
    Duplicate,
    TooLong
}

data class FolderUpdate(
    val state: ChatLibraryState,
    val issue: FolderNameIssue?
)

sealed interface ChatListFilter {
    data object Inbox : ChatListFilter

    data object Pinned : ChatListFilter

    data object Archived : ChatListFilter

    data class Folder(val folderId: String) : ChatListFilter
}

fun normalizedFolderName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

fun folderNameIssue(
    folders: List<ChatFolder>,
    name: String,
    ignoreId: String? = null
): FolderNameIssue? {
    val normalized = normalizedFolderName(name)
    if (normalized.isBlank()) return FolderNameIssue.Blank
    if (normalized.length > MAX_FOLDER_NAME_LENGTH) return FolderNameIssue.TooLong
    val duplicate = folders.any { folder ->
        folder.id != ignoreId && folder.name.equals(normalized, ignoreCase = true)
    }
    return if (duplicate) FolderNameIssue.Duplicate else null
}

fun ChatLibraryState.pin(chatId: Int): ChatLibraryState = copy(
    pinnedChatIds = pinnedChatIds + chatId,
    archivedChatIds = archivedChatIds - chatId
)

fun ChatLibraryState.unpin(chatId: Int): ChatLibraryState = copy(
    pinnedChatIds = pinnedChatIds - chatId
)

fun ChatLibraryState.archive(chatId: Int): ChatLibraryState = copy(
    archivedChatIds = archivedChatIds + chatId,
    pinnedChatIds = pinnedChatIds - chatId
)

fun ChatLibraryState.restore(chatId: Int): ChatLibraryState = copy(
    archivedChatIds = archivedChatIds - chatId
)

fun ChatLibraryState.markTemporary(chatId: Int): ChatLibraryState = copy(
    temporaryChatIds = temporaryChatIds + chatId,
    pinnedChatIds = pinnedChatIds - chatId
)

fun ChatLibraryState.forget(chatId: Int): ChatLibraryState = copy(
    pinnedChatIds = pinnedChatIds - chatId,
    archivedChatIds = archivedChatIds - chatId,
    temporaryChatIds = temporaryChatIds - chatId,
    chatFolderIds = chatFolderIds - chatId
)

fun ChatLibraryState.moveToFolder(chatId: Int, folderId: String?): ChatLibraryState {
    if (folderId == null) return copy(chatFolderIds = chatFolderIds - chatId)
    if (folders.none { it.id == folderId }) return this
    return copy(chatFolderIds = chatFolderIds + (chatId to folderId))
}

fun ChatLibraryState.addFolder(id: String, name: String): FolderUpdate {
    if (id.isBlank()) return FolderUpdate(this, FolderNameIssue.Blank)
    if (folders.any { it.id == id }) return FolderUpdate(this, FolderNameIssue.Duplicate)
    val issue = folderNameIssue(folders, name)
    if (issue != null) return FolderUpdate(this, issue)
    return FolderUpdate(
        state = copy(folders = folders + ChatFolder(id, normalizedFolderName(name))),
        issue = null
    )
}

fun ChatLibraryState.renameFolder(folderId: String, name: String): FolderUpdate {
    if (folders.none { it.id == folderId }) return FolderUpdate(this, null)
    val issue = folderNameIssue(folders, name, ignoreId = folderId)
    if (issue != null) return FolderUpdate(this, issue)
    val normalized = normalizedFolderName(name)
    return FolderUpdate(
        state = copy(
            folders = folders.map { folder ->
                if (folder.id == folderId) folder.copy(name = normalized) else folder
            }
        ),
        issue = null
    )
}

fun ChatLibraryState.deleteFolder(folderId: String): ChatLibraryState = copy(
    folders = folders.filterNot { it.id == folderId },
    chatFolderIds = chatFolderIds.filterValues { it != folderId }
)

fun <T> projectChatList(
    chats: List<T>,
    chatId: (T) -> Int,
    updatedAt: (T) -> Long,
    library: ChatLibraryState,
    filter: ChatListFilter,
    searching: Boolean
): List<T> {
    val visible = chats.filter { chat ->
        val id = chatId(chat)
        if (id in library.temporaryChatIds) return@filter false
        if (searching) return@filter true
        val archived = id in library.archivedChatIds
        val folderId = library.chatFolderIds[id]
        when (filter) {
            ChatListFilter.Inbox -> !archived && folderId == null
            ChatListFilter.Pinned -> !archived && id in library.pinnedChatIds
            ChatListFilter.Archived -> archived
            is ChatListFilter.Folder -> !archived && folderId == filter.folderId
        }
    }
    return visible.sortedWith(
        compareByDescending<T> { chatId(it) in library.pinnedChatIds }
            .thenByDescending { updatedAt(it) }
    )
}
