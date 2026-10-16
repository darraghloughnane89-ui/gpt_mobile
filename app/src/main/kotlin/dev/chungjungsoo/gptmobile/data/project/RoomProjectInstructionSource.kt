package dev.chungjungsoo.gptmobile.data.project

import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.instruction.ProjectInstructionSource
import javax.inject.Inject

/**
 * Fills [dev.chungjungsoo.gptmobile.data.instruction.InstructionStack]'s project slot
 * from the chat's project. Blank projects stay null so a platform-only prompt is unchanged.
 */
class RoomProjectInstructionSource @Inject constructor(
    private val chatRoomDao: ChatRoomV2Dao,
    private val projectContextProvider: ProjectContextProvider
) : ProjectInstructionSource {
    override suspend fun instructionsFor(chatId: Int): String? {
        if (chatId <= 0) return null
        val projectId = chatRoomDao.getChatRoom(chatId)?.projectId ?: return null
        if (projectId <= 0) return null
        return projectContextProvider.projectLayer(projectId).takeIf { it.isNotBlank() }
    }
}
