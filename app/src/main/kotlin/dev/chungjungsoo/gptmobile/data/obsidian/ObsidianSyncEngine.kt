package dev.chungjungsoo.gptmobile.data.obsidian

import dev.chungjungsoo.gptmobile.data.obsidian.bridge.BotNote
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.BotRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.ChatVaultSource
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.MemoryNote
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.MemoryRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.ProjectNote
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.ProjectRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.SkillNote
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.SkillRepository
import dev.chungjungsoo.gptmobile.data.obsidian.bridge.VaultChat
import dev.chungjungsoo.gptmobile.data.obsidian.github.githubCommitMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncReport(
    val message: String,
    val pulled: Int = 0,
    val pushed: Int = 0,
    val conflicts: Int = 0,
    val ok: Boolean = true
)

fun interface VaultSyncFactory {
    suspend fun open(): VaultSync
}

@Singleton
class ObsidianSyncEngine @Inject constructor(
    private val preferences: ObsidianSyncPreferences,
    private val indexStore: ObsidianSyncIndexStore,
    private val vaults: VaultSyncFactory,
    private val chats: ChatVaultSource,
    private val memories: MemoryRepository,
    private val skills: SkillRepository,
    private val projects: ProjectRepository,
    private val bots: BotRepository
) {
    private val mutex = Mutex()
    var clock: () -> Long = { System.currentTimeMillis() / 1000 }

    suspend fun sync(): SyncReport = mutex.withLock { syncLocked() }

    suspend fun resolve(id: String, choice: ConflictChoice): SyncReport = mutex.withLock {
        val index = indexStore.load()
        val conflict = index.conflicts.firstOrNull { it.id == id }
            ?: return@withLock SyncReport("That conflict is already resolved.")
        val local = VaultMarkdown.parse(conflict.localMarkdown)
        val remote = VaultMarkdown.parse(conflict.remoteMarkdown)
        if (local == null || remote == null) {
            return@withLock SyncReport("Could not read that conflict.", ok = false)
        }
        val chosen = when (choice) {
            ConflictChoice.KEEP_LOCAL -> local
            ConflictChoice.KEEP_REMOTE -> remote
            ConflictChoice.KEEP_BOTH -> VaultSyncPlanner.keepBoth(local, remote, clock())
        }
        var working = index.copy(conflicts = index.conflicts.filterNot { it.id == id })
        if (choice != ConflictChoice.KEEP_LOCAL) {
            working = apply(chosen, working)
        }
        val tracked = VaultMarkdown.track(chosen, conflict.path)
        val committed = vaults.open().commit(
            VaultCommit(
                message = githubCommitMessage(),
                files = listOf(VaultFileWrite(tracked.path, VaultMarkdown.render(tracked.document)))
            )
        )
        working = when (committed) {
            is VaultCommitResult.Ok -> working.withEntry(tracked).appendLog("Resolved ${conflict.path}.")
            is VaultCommitResult.Rejected -> working.appendLog(committed.message)
            is VaultCommitResult.Failed -> working.appendLog(committed.message)
        }
        working = working.copy(lastStatus = working.log.lastOrNull() ?: index.lastStatus)
        indexStore.save(working)
        SyncReport(
            message = working.lastStatus,
            conflicts = working.conflicts.size,
            ok = committed is VaultCommitResult.Ok
        )
    }

    private suspend fun syncLocked(): SyncReport {
        val config = preferences.load()
        val root = VaultPaths.normalizeRoot(config.rootFolder)
        val index = indexStore.load()
        val vault = vaults.open()
        val remoteFiles = when (val remoteRead = vault.readTree()) {
            is VaultReadResult.Failed -> {
                val failed = index.appendLog(remoteRead.message).copy(lastStatus = remoteRead.message)
                indexStore.save(failed)
                return SyncReport(remoteRead.message, ok = false)
            }
            is VaultReadResult.Ok -> remoteRead.files
        }
        val remoteNotes = remoteFiles.mapNotNull { file ->
            VaultMarkdown.parse(file.content)?.let { VaultMarkdown.track(it, file.path) }
        }
        val localNotes = collectLocal(root, index)
        val plan = VaultSyncPlanner.plan(
            local = localNotes,
            remote = remoteNotes,
            index = index.entries.associateBy { it.id }
        )
        var working = index
        plan.pulls.forEach { note ->
            working = apply(note.document, working)
            working = working.withEntry(note)
        }
        plan.pushes.filter { it.document.type == VaultNoteType.CHAT }.forEach { note ->
            working = apply(note.document, working)
        }
        val committed = if (plan.pushes.isEmpty()) {
            VaultCommitResult.Ok("unchanged")
        } else {
            vault.commit(
                VaultCommit(
                    message = githubCommitMessage(),
                    files = plan.pushes.map { note ->
                        VaultFileWrite(note.path, VaultMarkdown.render(note.document))
                    }
                )
            )
        }
        val pushed = if (committed is VaultCommitResult.Ok && committed.revision != "unchanged") {
            plan.pushes.forEach { working = working.withEntry(it) }
            plan.pushes.size
        } else {
            0
        }
        if (committed is VaultCommitResult.Rejected || committed is VaultCommitResult.Failed) {
            val reason = when (committed) {
                is VaultCommitResult.Rejected -> committed.message
                is VaultCommitResult.Failed -> committed.message
                else -> ""
            }
            working = working.appendLog(reason)
        }
        val remoteById = remoteNotes.associateBy { it.document.id }
        localNotes.forEach { local ->
            val remote = remoteById[local.document.id] ?: return@forEach
            if (remote.hash == local.hash && plan.conflicts.none { it.id == local.document.id }) {
                working = working.withEntry(local.copy(path = remote.path))
            }
        }
        val storedConflicts = plan.conflicts.map { conflict ->
            StoredConflict(
                id = conflict.id,
                path = conflict.path,
                type = conflict.type.wire,
                localMarkdown = VaultMarkdown.render(conflict.local.document),
                remoteMarkdown = VaultMarkdown.render(conflict.remote.document),
                localUpdated = conflict.local.document.updatedEpochSeconds,
                remoteUpdated = conflict.remote.document.updatedEpochSeconds,
                allowKeepBoth = conflict.allowKeepBoth
            )
        }
        val status = when (committed) {
            is VaultCommitResult.Rejected -> committed.message
            is VaultCommitResult.Failed -> committed.message
            is VaultCommitResult.Ok -> "Synced. Pulled ${plan.pulls.size}, pushed $pushed, ${storedConflicts.size} conflicts."
        }
        working = working.copy(conflicts = storedConflicts, lastStatus = status).appendLog(status)
        indexStore.save(working)
        return SyncReport(
            message = status,
            pulled = plan.pulls.size,
            pushed = pushed,
            conflicts = storedConflicts.size,
            ok = committed !is VaultCommitResult.Failed
        )
    }

    private suspend fun collectLocal(root: String, index: SyncIndex): List<TrackedNote> {
        val notes = mutableListOf<TrackedNote>()
        val paths = index.entries.associate { it.id to it.path }
        remapChats(chats.listChats(), index).forEach { chat ->
            val links = chat.links.ifEmpty { listOf(VaultPaths.projectWikiLink(root, chat.projectName)) }
            val document = VaultDocument(
                id = chat.id,
                type = VaultNoteType.CHAT,
                updatedEpochSeconds = chat.updatedAtEpochSeconds,
                tags = chat.tags.ifEmpty { listOf("robin/chat") },
                links = links,
                title = chat.title,
                project = chat.projectName,
                messages = chat.messages
            )
            val path = paths[chat.id] ?: VaultPaths.chat(
                root = root,
                project = chat.projectName,
                createdEpochSeconds = chat.createdAtEpochSeconds,
                title = chat.title,
                id = chat.id
            )
            notes += VaultMarkdown.track(document, path)
        }
        memories.listMemories().forEach { note ->
            notes += memoryNote(note, root, paths)
        }
        skills.listSkills().forEach { note ->
            notes += skillNote(note, root, paths)
        }
        projects.listProjects().forEach { note ->
            notes += projectNote(note, root, paths)
        }
        bots.listBots().forEach { note ->
            notes += botNote(note, root, paths)
        }
        return notes
    }

    private suspend fun apply(document: VaultDocument, index: SyncIndex): SyncIndex = when (document.type) {
        VaultNoteType.MEMORY -> {
            memories.upsertMemory(
                MemoryNote(
                    id = document.id,
                    title = document.title.ifBlank { document.name ?: document.id },
                    body = document.body,
                    tags = document.tags,
                    links = document.links,
                    projectName = document.project,
                    updatedEpochSeconds = document.updatedEpochSeconds
                )
            )
            index
        }
        VaultNoteType.SKILL -> {
            skills.upsertSkill(
                SkillNote(
                    id = document.id,
                    name = document.name ?: document.title.ifBlank { document.id },
                    description = document.description.orEmpty(),
                    body = document.body,
                    tags = document.tags,
                    links = document.links,
                    updatedEpochSeconds = document.updatedEpochSeconds
                )
            )
            index
        }
        VaultNoteType.PROJECT -> {
            projects.upsertProject(
                ProjectNote(
                    id = document.id,
                    name = document.name ?: document.title.ifBlank { document.id },
                    body = document.body,
                    tags = document.tags,
                    links = document.links,
                    updatedEpochSeconds = document.updatedEpochSeconds
                )
            )
            index
        }
        VaultNoteType.BOT -> {
            bots.upsertBot(
                BotNote(
                    id = document.id,
                    name = document.name ?: document.title.ifBlank { document.id },
                    instructions = document.body,
                    tags = document.tags,
                    links = document.links,
                    updatedEpochSeconds = document.updatedEpochSeconds
                )
            )
            index
        }
        VaultNoteType.CHAT -> applyChat(document, index)
    }

    private suspend fun applyChat(document: VaultDocument, index: SyncIndex): SyncIndex {
        val knownRoom = index.chatRoomIds[document.id]
        val parsedRoom = document.id.removePrefix("chat-").toIntOrNull()?.takeIf { chats.roomExists(it) }
        val roomId = knownRoom ?: parsedRoom ?: chats.createChat(document.title, document.updatedEpochSeconds)
        var links = index.chatMessageLinks[document.id].orEmpty()
        val roomMessageIds = chats.messageIds(roomId)
        document.messages.forEach { message ->
            val linked = links[message.id]
            val embedded = message.id.removePrefix("m").toIntOrNull()?.takeIf { it in roomMessageIds }
            val existing = linked ?: embedded
            if (existing != null) {
                chats.updateMessage(roomId, existing, message)
            } else {
                val created = chats.appendMessage(roomId, message)
                links = links + (message.id to created)
            }
        }
        chats.updateChat(roomId, document.title, document.updatedEpochSeconds)
        return index.copy(
            chatRoomIds = index.chatRoomIds + (document.id to roomId),
            chatMessageLinks = index.chatMessageLinks + (document.id to links)
        )
    }

    private fun remapChats(chats: List<VaultChat>, index: SyncIndex): List<VaultChat> {
        val roomToVault = index.chatRoomIds.entries.associate { (vaultId, roomId) -> roomId to vaultId }
        return chats.map { chat ->
            val roomId = chat.id.removePrefix("chat-").toIntOrNull()
            val vaultId = roomId?.let { roomToVault[it] } ?: chat.id
            val roomToMessage = index.chatMessageLinks[vaultId].orEmpty()
                .entries
                .associate { (vaultMessageId, storedRoomId) -> storedRoomId to vaultMessageId }
            chat.copy(
                id = vaultId,
                messages = chat.messages.map { message ->
                    val storedRoomId = message.id.removePrefix("m").toIntOrNull()
                    val vaultMessageId = storedRoomId?.let { roomToMessage[it] }
                    if (vaultMessageId != null) message.copy(id = vaultMessageId) else message
                }
            )
        }
    }

    private fun memoryNote(note: MemoryNote, root: String, paths: Map<String, String>): TrackedNote {
        val links = note.links.ifEmpty {
            note.projectName?.let { listOf(VaultPaths.projectWikiLink(root, it)) }.orEmpty()
        }
        return VaultMarkdown.track(
            VaultDocument(
                id = note.id,
                type = VaultNoteType.MEMORY,
                updatedEpochSeconds = note.updatedEpochSeconds,
                tags = note.tags.ifEmpty { listOf("robin/memory") },
                links = links,
                title = note.title,
                project = note.projectName,
                body = note.body
            ),
            paths[note.id] ?: VaultPaths.memory(root, note.title, note.id)
        )
    }

    private fun skillNote(note: SkillNote, root: String, paths: Map<String, String>): TrackedNote = VaultMarkdown.track(
        VaultDocument(
            id = note.id,
            type = VaultNoteType.SKILL,
            updatedEpochSeconds = note.updatedEpochSeconds,
            tags = note.tags.ifEmpty { listOf("robin/skill") },
            links = note.links,
            title = note.name,
            name = note.name,
            description = note.description,
            body = note.body
        ),
        paths[note.id] ?: VaultPaths.skill(root, note.name, note.id)
    )

    private fun projectNote(note: ProjectNote, root: String, paths: Map<String, String>): TrackedNote = VaultMarkdown.track(
        VaultDocument(
            id = note.id,
            type = VaultNoteType.PROJECT,
            updatedEpochSeconds = note.updatedEpochSeconds,
            tags = note.tags.ifEmpty { listOf("robin/project") },
            links = note.links,
            title = note.name,
            name = note.name,
            body = note.body
        ),
        paths[note.id] ?: VaultPaths.project(root, note.name, note.id)
    )

    private fun botNote(note: BotNote, root: String, paths: Map<String, String>): TrackedNote = VaultMarkdown.track(
        VaultDocument(
            id = note.id,
            type = VaultNoteType.BOT,
            updatedEpochSeconds = note.updatedEpochSeconds,
            tags = note.tags.ifEmpty { listOf("robin/bot") },
            links = note.links,
            title = note.name,
            name = note.name,
            body = note.instructions
        ),
        paths[note.id] ?: VaultPaths.bot(root, note.name, note.id)
    )

    private fun SyncIndex.withEntry(note: TrackedNote): SyncIndex {
        val entry = SyncIndexEntry(
            id = note.document.id,
            path = note.path,
            type = note.document.type.wire,
            baseHash = note.hash
        )
        return copy(entries = entries.filterNot { it.id == entry.id } + entry)
    }

    private fun SyncIndex.appendLog(line: String): SyncIndex {
        if (line.isBlank()) return this
        return copy(log = log + "${VaultMarkdown.formatEpoch(clock())} $line")
    }
}
