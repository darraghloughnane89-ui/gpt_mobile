package dev.chungjungsoo.gptmobile.data.obsidian

enum class VaultNoteType {
    CHAT,
    MEMORY,
    SKILL,
    BOT,
    PROJECT;

    val wire: String = name.lowercase()

    companion object {
        fun fromWire(value: String?): VaultNoteType? =
            entries.firstOrNull { it.wire == value?.trim()?.lowercase() }
    }
}

enum class VaultBackend {
    GITHUB,
    LOCAL;

    val storageKey: String = name.lowercase()

    companion object {
        fun fromStorage(value: String?): VaultBackend = when (value?.trim()?.lowercase()) {
            "local", "folder", "saf" -> LOCAL
            else -> GITHUB
        }
    }
}

data class VaultMessage(
    val id: String,
    val role: String,
    val content: String,
    val atEpochSeconds: Long,
    val platform: String? = null
)

data class VaultDocument(
    val id: String,
    val type: VaultNoteType,
    val updatedEpochSeconds: Long,
    val tags: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val title: String = "",
    val project: String? = null,
    val name: String? = null,
    val description: String? = null,
    val messages: List<VaultMessage> = emptyList(),
    val body: String = "",
    val extra: Map<String, String> = emptyMap()
)

data class TrackedNote(
    val path: String,
    val hash: String,
    val document: VaultDocument
)

data class VaultRemoteFile(
    val path: String,
    val content: String,
    val transportSha: String
)

data class VaultFileWrite(
    val path: String,
    val content: String
)

data class VaultCommit(
    val message: String,
    val files: List<VaultFileWrite>
)

sealed class VaultReadResult {
    data class Ok(val files: List<VaultRemoteFile>) : VaultReadResult()
    data class Failed(val message: String) : VaultReadResult()
}

sealed class VaultCommitResult {
    data class Ok(val revision: String) : VaultCommitResult()
    data class Rejected(val message: String) : VaultCommitResult()
    data class Failed(val message: String) : VaultCommitResult()
}

interface VaultSync {
    suspend fun readTree(): VaultReadResult
    suspend fun commit(change: VaultCommit): VaultCommitResult
}

data class PlannedConflict(
    val id: String,
    val path: String,
    val type: VaultNoteType,
    val local: TrackedNote,
    val remote: TrackedNote,
    val allowKeepBoth: Boolean
)

data class SyncPlan(
    val pulls: List<TrackedNote> = emptyList(),
    val pushes: List<TrackedNote> = emptyList(),
    val conflicts: List<PlannedConflict> = emptyList()
)

enum class ConflictChoice {
    KEEP_LOCAL,
    KEEP_REMOTE,
    KEEP_BOTH
}
