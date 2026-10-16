package dev.chungjungsoo.gptmobile.data.obsidian

import dev.chungjungsoo.gptmobile.util.Brand
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Sync cursors live in app files, not in chat_v2. Room versions 12–18 belong to
 * other streams, and version 19 is only for a later sync-metadata table if a
 * coordinator still wants one. A file keeps this branch on database version 11.
 */
@Serializable
data class SyncIndexEntry(
    val id: String,
    val path: String,
    val type: String,
    val baseHash: String
)

@Serializable
data class StoredConflict(
    val id: String,
    val path: String,
    val type: String,
    val localMarkdown: String,
    val remoteMarkdown: String,
    val localUpdated: Long,
    val remoteUpdated: Long,
    val allowKeepBoth: Boolean
)

@Serializable
data class SyncIndex(
    val entries: List<SyncIndexEntry> = emptyList(),
    val conflicts: List<StoredConflict> = emptyList(),
    val log: List<String> = emptyList(),
    val lastStatus: String = "Not synced yet",
    val chatRoomIds: Map<String, Int> = emptyMap(),
    val chatMessageLinks: Map<String, Map<String, Int>> = emptyMap()
)

@Serializable
data class ObsidianSyncConfig(
    val backend: String = VaultBackend.GITHUB.storageKey,
    val rootFolder: String = Brand.VAULT_ROOT,
    val githubOwner: String = Brand.VAULT_GITHUB_OWNER,
    val githubRepo: String = Brand.VAULT_GITHUB_REPO,
    val githubBranch: String = Brand.VAULT_GITHUB_BRANCH,
    val localTreeUri: String? = null
) {
    val vaultBackend: VaultBackend
        get() = VaultBackend.fromStorage(backend)
}

interface ObsidianSyncPreferences {
    suspend fun load(): ObsidianSyncConfig
    suspend fun save(config: ObsidianSyncConfig)
}

interface ObsidianSyncIndexStore {
    suspend fun load(): SyncIndex
    suspend fun save(index: SyncIndex)
}

class FileObsidianSyncPreferences(
    private val file: File
) : ObsidianSyncPreferences {
    private val mutex = Mutex()

    override suspend fun load(): ObsidianSyncConfig = mutex.withLock {
        if (!file.exists()) return ObsidianSyncConfig()
        runCatching { json.decodeFromString<ObsidianSyncConfig>(file.readText()) }
            .getOrElse { ObsidianSyncConfig() }
    }

    override suspend fun save(config: ObsidianSyncConfig) = mutex.withLock {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(config))
    }
}

class FileObsidianSyncIndexStore(
    private val file: File
) : ObsidianSyncIndexStore {
    private val mutex = Mutex()

    override suspend fun load(): SyncIndex = mutex.withLock {
        if (!file.exists()) return SyncIndex()
        runCatching { json.decodeFromString<SyncIndex>(file.readText()) }.getOrElse { SyncIndex() }
    }

    override suspend fun save(index: SyncIndex) = mutex.withLock {
        file.parentFile?.mkdirs()
        val trimmed = index.copy(log = index.log.takeLast(MAX_LOG_LINES))
        file.writeText(json.encodeToString(trimmed))
    }

    private companion object {
        const val MAX_LOG_LINES = 100
    }
}

private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}
