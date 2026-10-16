package dev.chungjungsoo.gptmobile.data.obsidian

object VaultSyncPlanner {
    fun plan(
        local: List<TrackedNote>,
        remote: List<TrackedNote>,
        index: Map<String, SyncIndexEntry>
    ): SyncPlan {
        val localById = local.associateBy { it.document.id }
        val remoteById = remote.associateBy { it.document.id }
        val ids = (localById.keys + remoteById.keys).sorted()
        val pulls = mutableListOf<TrackedNote>()
        val pushes = mutableListOf<TrackedNote>()
        val conflicts = mutableListOf<PlannedConflict>()

        ids.forEach { id ->
            val localNote = localById[id]
            val remoteNote = remoteById[id]
            val base = index[id]
            val path = remoteNote?.path ?: base?.path ?: localNote?.path ?: return@forEach
            when {
                localNote == null && remoteNote != null -> pulls += remoteNote.copy(path = path)
                localNote != null && remoteNote == null -> pushes += localNote.copy(path = path)
                localNote != null && remoteNote != null && localNote.hash == remoteNote.hash -> Unit
                localNote != null && remoteNote != null -> {
                    val localChanged = base == null || localNote.hash != base.baseHash
                    val remoteChanged = base == null || remoteNote.hash != base.baseHash
                    when {
                        base != null && !localChanged && remoteChanged -> pulls += remoteNote.copy(path = path)
                        base != null && localChanged && !remoteChanged -> pushes += localNote.copy(path = path)
                        else -> resolveDivergence(localNote, remoteNote, path, pulls, pushes, conflicts)
                    }
                }
            }
        }
        return SyncPlan(pulls = pulls, pushes = pushes, conflicts = conflicts)
    }

    fun mergeChat(base: List<VaultMessage>?, local: List<VaultMessage>, remote: List<VaultMessage>): List<VaultMessage>? {
        val baseById = base?.associateBy { it.id }.orEmpty()
        val localById = local.associateBy { it.id }
        val remoteById = remote.associateBy { it.id }
        if (localById.size != local.size || remoteById.size != remote.size) return null
        val merged = mutableListOf<VaultMessage>()
        (baseById.keys + localById.keys + remoteById.keys).forEach { id ->
            val baseMessage = baseById[id]
            val localMessage = localById[id]
            val remoteMessage = remoteById[id]
            val chosen = when {
                localMessage != null && remoteMessage != null && localMessage == remoteMessage -> localMessage
                localMessage != null && remoteMessage != null && baseMessage == localMessage -> remoteMessage
                localMessage != null && remoteMessage != null && baseMessage == remoteMessage -> localMessage
                localMessage != null && remoteMessage == null && baseMessage == null -> localMessage
                localMessage == null && remoteMessage != null && baseMessage == null -> remoteMessage
                else -> return null
            }
            merged += chosen
        }
        return merged.sortedWith(compareBy({ it.atEpochSeconds }, { it.id }))
    }

    fun keepBoth(local: VaultDocument, remote: VaultDocument, nowEpochSeconds: Long): VaultDocument {
        val merged = if (local.type == VaultNoteType.CHAT) {
            keepBothMessages(local.messages, remote.messages)
        } else {
            local.messages
        }
        val body = if (local.type == VaultNoteType.CHAT) {
            ""
        } else {
            buildString {
                append(local.body.trimEnd())
                if (remote.body.isNotBlank() && remote.body.trim() != local.body.trim()) {
                    if (isNotEmpty()) append("\n\n")
                    append("## Vault copy\n\n")
                    append(remote.body.trim())
                }
            }
        }
        return local.copy(
            updatedEpochSeconds = nowEpochSeconds,
            messages = merged,
            body = body,
            links = (local.links + remote.links).distinct(),
            tags = (local.tags + remote.tags).distinct()
        )
    }

    private fun keepBothMessages(local: List<VaultMessage>, remote: List<VaultMessage>): List<VaultMessage> {
        val localById = local.associateBy { it.id }
        val combined = local.toMutableList()
        remote.forEach { message ->
            val existing = localById[message.id]
            if (existing == null) {
                combined += message
            } else if (existing != message) {
                combined += message.copy(id = "${message.id}#vault")
            }
        }
        return combined.sortedWith(compareBy({ it.atEpochSeconds }, { it.id }))
    }

    private fun resolveDivergence(
        localNote: TrackedNote,
        remoteNote: TrackedNote,
        path: String,
        pulls: MutableList<TrackedNote>,
        pushes: MutableList<TrackedNote>,
        conflicts: MutableList<PlannedConflict>
    ) {
        if (localNote.document.type == VaultNoteType.CHAT && remoteNote.document.type == VaultNoteType.CHAT) {
            val merged = mergeChat(null, localNote.document.messages, remoteNote.document.messages)
            if (merged != null) {
                val updated = maxOf(localNote.document.updatedEpochSeconds, remoteNote.document.updatedEpochSeconds)
                val document = localNote.document.copy(messages = merged, updatedEpochSeconds = updated)
                pushes += VaultMarkdown.track(document, path)
                return
            }
        }
        conflicts += PlannedConflict(
            id = localNote.document.id,
            path = path,
            type = localNote.document.type,
            local = localNote.copy(path = path),
            remote = remoteNote.copy(path = path),
            allowKeepBoth = true
        )
    }
}
