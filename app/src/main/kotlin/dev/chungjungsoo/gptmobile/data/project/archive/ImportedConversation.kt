package dev.chungjungsoo.gptmobile.data.project.archive

object ImportedConversation {
    suspend fun persist(
        messages: List<ImportedMessage>,
        assistantPlatformUid: String,
        clear: suspend () -> Unit,
        insert: suspend (text: String, createdAt: Long, platformType: String?, linkedMessageId: Int) -> Int
    ) {
        clear()
        var lastUserId = 0
        messages.forEach { message ->
            val text = message.text.trim()
            if (text.isEmpty()) return@forEach
            val user = message.role == ImportedRole.USER
            val id = insert(
                text,
                message.createdAt,
                if (user) null else assistantPlatformUid,
                if (user) 0 else lastUserId
            )
            if (user) lastUserId = id
        }
    }
}
