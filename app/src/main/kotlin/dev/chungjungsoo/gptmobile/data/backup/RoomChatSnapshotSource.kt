package dev.chungjungsoo.gptmobile.data.backup

import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.memory.MemoryDao
import dev.chungjungsoo.gptmobile.data.memory.MemoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomChatSnapshotSource @Inject constructor(
    private val platformDao: PlatformV2Dao,
    private val chatRoomDao: ChatRoomV2Dao,
    private val messageDao: MessageV2Dao,
    private val memoryDao: MemoryDao,
    private val memoryRepository: MemoryRepository
) : ChatSnapshotSource {
    override suspend fun snapshot(nowEpochSeconds: Long): ChatArchive {
        val chats = chatRoomDao.getChatRooms().map { room ->
            ArchiveChat(
                title = room.title,
                enabledPlatform = room.enabledPlatform,
                createdAt = room.createdAt,
                updatedAt = room.updatedAt,
                memoryEnabled = memoryDao.memoryEnabled(room.id) != false,
                messages = messageDao.loadMessages(room.id).map(ChatArchiveCodec::message)
            )
        }
        return ChatArchive(
            exportedAtEpochSeconds = nowEpochSeconds,
            platforms = platformDao.getPlatforms().map(ChatArchiveCodec::platform),
            chats = chats,
            memories = memoryDao.getAll().map(ChatArchiveCodec::memory)
        )
    }

    override suspend fun importAdditive(archive: ChatArchive): ArchiveImportCounts {
        val existing = platformDao.getPlatforms().map { it.uid }.toSet()
        val platforms = ChatArchiveCodec.platformsToInsert(existing, archive.platforms)
        platforms.forEach { platformDao.addPlatform(it) }

        var chatsAdded = 0
        for (chat in archive.chats) {
            val newChatId = chatRoomDao.addChatRoom(
                ChatRoomV2(
                    title = chat.title,
                    enabledPlatform = chat.enabledPlatform,
                    createdAt = chat.createdAt,
                    updatedAt = chat.updatedAt
                )
            ).toInt()
            val idMap = mutableMapOf<Int, Int>()
            val stored = mutableListOf<MessageV2>()
            for (message in chat.messages) {
                val entity = message.toEntity(newChatId)
                val newId = messageDao.addMessage(entity).toInt()
                idMap[message.id] = newId
                stored += entity.copy(id = newId)
            }
            for (storedMessage in stored) {
                val original = chat.messages.first { idMap[it.id] == storedMessage.id }
                val linked = idMap[original.linkedMessageId] ?: 0
                if (linked != 0) {
                    messageDao.editMessages(storedMessage.copy(linkedMessageId = linked))
                }
            }
            if (!chat.memoryEnabled) {
                memoryRepository.setEnabledForChat(newChatId, false)
            }
            chatsAdded++
        }

        var memoriesWritten = 0
        for (memory in archive.memories) {
            val saved = memoryRepository.importMemory(
                text = memory.text,
                source = memory.source,
                projectId = memory.projectId,
                paused = memory.paused,
                createdAt = memory.createdAt,
                updatedAt = memory.updatedAt
            )
            if (saved != null) memoriesWritten++
        }
        return ArchiveImportCounts(
            platformsAdded = platforms.size,
            chatsAdded = chatsAdded,
            memoriesWritten = memoriesWritten
        )
    }

    private fun ArchiveMessage.toEntity(chatId: Int) = MessageV2(
        chatId = chatId,
        thoughts = thoughts,
        content = content,
        linkedMessageId = 0,
        platformType = platformType,
        createdAt = createdAt,
        revisions = revisions,
        activeRevisionIndex = activeRevisionIndex,
        timeline = timeline
    )
}
