package dev.chungjungsoo.gptmobile.data.obsidian.bridge

import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.obsidian.VaultMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomChatVaultSource @Inject constructor(
    private val chatRoomDao: ChatRoomV2Dao,
    private val messageDao: MessageV2Dao,
    private val projectRepository: ProjectRepository
) : ChatVaultSource {

    override suspend fun listChats(): List<VaultChat> = chatRoomDao.getChatRooms().map { room ->
        val project = projectRepository.projectNameForChat("chat-${room.id}") ?: DEFAULT_PROJECT
        VaultChat(
            id = "chat-${room.id}",
            title = room.title.ifBlank { "Chat ${room.id}" },
            projectName = project,
            createdAtEpochSeconds = room.createdAt,
            updatedAtEpochSeconds = room.updatedAt,
            messages = messageDao.loadMessages(room.id).mapNotNull { message ->
                val content = message.effectiveContent()
                if (content.isBlank()) return@mapNotNull null
                VaultMessage(
                    id = "m${message.id}",
                    role = if (message.platformType == null) "user" else "assistant",
                    content = content,
                    atEpochSeconds = message.createdAt,
                    platform = message.platformType
                )
            }
        )
    }

    override suspend fun roomExists(roomId: Int): Boolean =
        chatRoomDao.getChatRoom(roomId) != null

    override suspend fun createChat(title: String, updatedEpochSeconds: Long): Int {
        val id = chatRoomDao.addChatRoom(
            ChatRoomV2(
                title = title.ifBlank { "Chat" },
                enabledPlatform = emptyList(),
                createdAt = updatedEpochSeconds,
                updatedAt = updatedEpochSeconds
            )
        )
        return id.toInt()
    }

    override suspend fun appendMessage(roomId: Int, message: VaultMessage): Int {
        val id = messageDao.addMessage(message.toEntity(roomId))
        return id.toInt()
    }

    override suspend fun updateMessage(roomId: Int, roomMessageId: Int, message: VaultMessage) {
        val existing = messageDao.loadMessages(roomId).firstOrNull { it.id == roomMessageId } ?: return
        messageDao.editMessages(
            existing.copy(
                content = message.content,
                platformType = if (message.role == "user") null else message.platform ?: existing.platformType ?: "vault",
                createdAt = message.atEpochSeconds
            )
        )
    }

    override suspend fun messageIds(roomId: Int): Set<Int> =
        messageDao.loadMessages(roomId).map { it.id }.toSet()

    override suspend fun updateChat(roomId: Int, title: String, updatedEpochSeconds: Long) {
        val room = chatRoomDao.getChatRoom(roomId) ?: return
        chatRoomDao.editChatRoom(
            room.copy(
                title = title.ifBlank { room.title },
                updatedAt = maxOf(room.updatedAt, updatedEpochSeconds)
            )
        )
    }

    private fun VaultMessage.toEntity(roomId: Int): MessageV2 = MessageV2(
        chatId = roomId,
        content = content,
        platformType = if (role == "user") null else platform ?: "vault",
        createdAt = atEpochSeconds
    )

    private companion object {
        const val DEFAULT_PROJECT = "Inbox"
    }
}
