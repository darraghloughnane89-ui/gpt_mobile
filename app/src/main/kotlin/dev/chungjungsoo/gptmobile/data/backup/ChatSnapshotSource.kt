package dev.chungjungsoo.gptmobile.data.backup

interface ChatSnapshotSource {
    suspend fun snapshot(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): ChatArchive
    suspend fun importAdditive(archive: ChatArchive): ArchiveImportCounts

    suspend fun exportJson(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): String = ChatArchiveCodec.encode(snapshot(nowEpochSeconds))

    suspend fun importJson(text: String): ArchiveImportCounts = importAdditive(ChatArchiveCodec.decode(text))
}
