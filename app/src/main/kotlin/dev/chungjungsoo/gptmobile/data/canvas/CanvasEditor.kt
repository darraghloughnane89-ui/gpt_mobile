package dev.chungjungsoo.gptmobile.data.canvas

fun createCanvas(
    library: CanvasLibrary,
    chatRoomId: Int,
    title: String,
    kind: CanvasKind,
    language: String,
    content: String,
    source: CanvasVersionSource,
    now: Long,
    newId: () -> String
): Pair<CanvasLibrary, CanvasDocument> {
    val document = newDocument(
        chatRoomId = chatRoomId,
        title = cleanCanvasTitle(title),
        kind = kind,
        language = language.ifBlank { if (kind == CanvasKind.Markdown) "markdown" else "text" },
        content = content.take(CanvasLimits.MAX_CONTENT_CHARS),
        source = source,
        now = now,
        newId = newId
    )
    return library.copy(documents = library.documents + document) to document
}

fun editCanvasContent(
    library: CanvasLibrary,
    documentId: String,
    content: String,
    now: Long,
    newId: () -> String
): CanvasLibrary {
    if (content.length > CanvasLimits.MAX_CONTENT_CHARS) return library
    val document = library.documents.firstOrNull { it.id == documentId } ?: return library
    if (document.proposal != null || content == document.content) return library
    val last = document.versions.lastOrNull() ?: return library
    val versions = if (
        last.source == CanvasVersionSource.User &&
        now - last.createdAt <= CanvasLimits.COALESCE_USER_EDITS_MS
    ) {
        document.versions.dropLast(1) + last.copy(content = content, createdAt = now)
    } else {
        trimVersions(
            document.versions + CanvasVersion(
                id = newId(),
                content = content,
                title = document.title,
                createdAt = now,
                source = CanvasVersionSource.User
            )
        )
    }
    return library.replace(document.copy(versions = versions, updatedAt = now, pendingSelection = null))
}

fun editCanvasTitle(library: CanvasLibrary, documentId: String, title: String, now: Long): CanvasLibrary {
    val document = library.documents.firstOrNull { it.id == documentId } ?: return library
    val cleaned = cleanCanvasTitle(title, fallback = document.title)
    if (cleaned == document.title) return library
    val versions = document.versions.mapIndexed { index, version ->
        if (index == document.versions.lastIndex) version.copy(title = cleaned) else version
    }
    return library.replace(document.copy(title = cleaned, versions = versions, updatedAt = now))
}

fun rememberCanvasSelection(
    library: CanvasLibrary,
    documentId: String,
    selection: PendingSelection
): CanvasLibrary {
    val document = library.documents.firstOrNull { it.id == documentId } ?: return library
    if (selection.text.isBlank()) return library
    return library.replace(document.copy(pendingSelection = selection))
}

fun clearCanvasSelection(library: CanvasLibrary, documentId: String): CanvasLibrary {
    val document = library.documents.firstOrNull { it.id == documentId } ?: return library
    if (document.pendingSelection == null) return library
    return library.replace(document.copy(pendingSelection = null))
}

fun acceptCanvasProposal(
    library: CanvasLibrary,
    documentId: String,
    now: Long,
    newId: () -> String
): CanvasLibrary {
    val document = library.documents.firstOrNull { it.id == documentId } ?: return library
    val proposal = document.proposal ?: return library
    val versions = trimVersions(
        document.versions + CanvasVersion(
            id = newId(),
            content = proposal.proposedContent,
            title = document.title,
            createdAt = now,
            source = CanvasVersionSource.Model
        )
    )
    return library.replace(
        document.copy(
            versions = versions,
            proposal = null,
            pendingSelection = null,
            updatedAt = now
        )
    )
}

fun rejectCanvasProposal(library: CanvasLibrary, documentId: String): CanvasLibrary {
    val document = library.documents.firstOrNull { it.id == documentId } ?: return library
    if (document.proposal == null && document.pendingSelection == null) return library
    return library.replace(document.copy(proposal = null, pendingSelection = null))
}

fun restoreCanvasVersion(
    library: CanvasLibrary,
    documentId: String,
    versionId: String,
    now: Long,
    newId: () -> String
): CanvasLibrary {
    val document = library.documents.firstOrNull { it.id == documentId } ?: return library
    val version = document.versions.firstOrNull { it.id == versionId } ?: return library
    if (version.content == document.content && document.proposal == null) return library
    val versions = trimVersions(
        document.versions + CanvasVersion(
            id = newId(),
            content = version.content,
            title = version.title,
            createdAt = now,
            source = CanvasVersionSource.User
        )
    )
    return library.replace(
        document.copy(
            title = version.title,
            versions = versions,
            proposal = null,
            pendingSelection = null,
            updatedAt = now
        )
    )
}

fun reassignCanvasChat(library: CanvasLibrary, fromChatRoomId: Int, toChatRoomId: Int): CanvasLibrary {
    if (fromChatRoomId == toChatRoomId) return library
    if (library.documents.none { it.chatRoomId == fromChatRoomId }) return library
    return library.copy(
        documents = library.documents.map { document ->
            if (document.chatRoomId == fromChatRoomId) document.copy(chatRoomId = toChatRoomId) else document
        }
    )
}

fun ingestCanvasMessages(
    library: CanvasLibrary,
    chatRoomId: Int,
    olderMessages: List<String>,
    latestMessages: List<String>,
    now: Long,
    newId: () -> String
): Pair<CanvasLibrary, String?> {
    var current = library
    olderMessages.forEach { message ->
        current = markDirectiveFingerprints(current, message)
    }
    var focusedId: String? = null
    latestMessages.forEach { message ->
        val applied = applyMessage(current, chatRoomId, message, now, newId, focus = true, force = false)
        current = applied.first
        if (applied.second != null) focusedId = applied.second
    }
    return current to focusedId
}

fun openCanvasFromAssistant(
    library: CanvasLibrary,
    chatRoomId: Int,
    content: String,
    now: Long,
    newId: () -> String,
    untitled: String = "Untitled canvas"
): Pair<CanvasLibrary, OpenCanvasResult> = when (val plan = planAssistantOpen(content)) {
    AssistantCanvasPlan.Empty -> library to OpenCanvasResult.Empty
    AssistantCanvasPlan.PreviewOwned -> library to OpenCanvasResult.PreviewOwned
    is AssistantCanvasPlan.Directives -> openDirectives(library, chatRoomId, content, plan.directives, now, newId)

    is AssistantCanvasPlan.Seed -> openSeed(library, chatRoomId, plan.seed, now, newId)
    is AssistantCanvasPlan.WholeMarkdown -> {
        val seed = CanvasSeed(
            title = cleanCanvasTitle(plan.content.lineSequence().firstOrNull(), untitled),
            kind = CanvasKind.Markdown,
            language = "markdown",
            content = plan.content
        )
        openSeed(library, chatRoomId, seed, now, newId)
    }
}

private fun openSeed(
    library: CanvasLibrary,
    chatRoomId: Int,
    seed: CanvasSeed,
    now: Long,
    newId: () -> String
): Pair<CanvasLibrary, OpenCanvasResult> {
    val existing = library.documents.lastOrNull { document ->
        document.chatRoomId == chatRoomId &&
            document.kind == seed.kind &&
            document.content == seed.content &&
            document.title == seed.title
    }
    if (existing != null) return library to OpenCanvasResult.Opened(existing)
    val created = createCanvas(
        library = library,
        chatRoomId = chatRoomId,
        title = seed.title,
        kind = seed.kind,
        language = seed.language,
        content = seed.content,
        source = CanvasVersionSource.Open,
        now = now,
        newId = newId
    )
    return created.first to OpenCanvasResult.Opened(created.second)
}

private fun openDirectives(
    library: CanvasLibrary,
    chatRoomId: Int,
    content: String,
    directives: List<CanvasDirective>,
    now: Long,
    newId: () -> String
): Pair<CanvasLibrary, OpenCanvasResult> {
    val known = directives.lastOrNull()?.let { directive ->
        library.documents.lastOrNull { document ->
            document.chatRoomId == chatRoomId && document.lastDirectiveFingerprint == directive.fingerprint
        } ?: directive.id?.let { id ->
            library.documents.firstOrNull { it.id == id && it.chatRoomId == chatRoomId }
        }
    }
    if (known != null && directives.all { it.fingerprint in library.appliedFingerprints }) {
        return library to OpenCanvasResult.Opened(known)
    }
    val applied = applyMessage(library, chatRoomId, content, now, newId, focus = true, force = true)
    val document = applied.second?.let { id -> applied.first.documents.firstOrNull { it.id == id } }
        ?: existingDocumentForDirectives(applied.first, chatRoomId, directives)
    return if (document == null) {
        applied.first to OpenCanvasResult.Empty
    } else {
        applied.first to OpenCanvasResult.Opened(document)
    }
}

private fun markDirectiveFingerprints(library: CanvasLibrary, message: String): CanvasLibrary {
    var current = library
    extractCanvasDirectives(message).forEach { directive ->
        current = rememberFingerprint(current, directive.fingerprint)
    }
    return current
}

private fun applyMessage(
    library: CanvasLibrary,
    chatRoomId: Int,
    message: String,
    now: Long,
    newId: () -> String,
    focus: Boolean,
    force: Boolean
): Pair<CanvasLibrary, String?> {
    var current = library
    var focused: String? = null
    extractCanvasDirectives(message).forEach { directive ->
        if (!force && directive.fingerprint in current.appliedFingerprints) return@forEach
        val applied = applyDirective(current, chatRoomId, directive, now, newId)
        current = rememberFingerprint(applied.first, directive.fingerprint)
        if (focus && applied.second != null) focused = applied.second
    }
    return current to focused
}

private fun applyDirective(
    library: CanvasLibrary,
    chatRoomId: Int,
    directive: CanvasDirective,
    now: Long,
    newId: () -> String
): Pair<CanvasLibrary, String?> {
    if (directive.body.length > CanvasLimits.MAX_CONTENT_CHARS) return library to null
    val existing = directive.id?.let { id ->
        library.documents.firstOrNull { it.id == id && it.chatRoomId == chatRoomId }
    } ?: directive.id?.let { id -> library.documents.firstOrNull { it.id == id } }
    return when (directive.action) {
        CanvasAction.Open -> if (existing == null) {
            val created = createCanvas(
                library = library,
                chatRoomId = chatRoomId,
                title = directive.title ?: "Untitled canvas",
                kind = directive.kind ?: CanvasKind.Markdown,
                language = directive.language ?: if (directive.kind == CanvasKind.Code) "text" else "markdown",
                content = directive.body,
                source = CanvasVersionSource.Open,
                now = now,
                newId = newId
            )
            created.first.replace(
                created.second.copy(lastDirectiveFingerprint = directive.fingerprint)
            ) to created.second.id
        } else {
            propose(library, existing, directive.body, directive.fingerprint)
        }

        CanvasAction.Propose -> {
            val target = existing ?: library.latestFor(chatRoomId)
            if (target == null) {
                applyDirective(library, chatRoomId, directive.copy(action = CanvasAction.Open, id = null), now, newId)
            } else {
                propose(library, target, directive.body, directive.fingerprint)
            }
        }

        CanvasAction.ProposeSelection -> {
            val target = existing ?: library.latestFor(chatRoomId) ?: return library to null
            val spliced = target.pendingSelection?.let { selection ->
                spliceSelection(target.content, selection, directive.body)
            }
            propose(library, target, spliced ?: directive.body, directive.fingerprint)
        }
    }
}

private fun propose(
    library: CanvasLibrary,
    document: CanvasDocument,
    proposedContent: String,
    fingerprint: String
): Pair<CanvasLibrary, String?> {
    if (proposedContent.length > CanvasLimits.MAX_CONTENT_CHARS) return library to null
    if (proposedContent == document.content) {
        return library.replace(document.copy(lastDirectiveFingerprint = fingerprint, proposal = null)) to document.id
    }
    val proposal = CanvasProposal(
        baseVersionId = document.activeVersionId,
        proposedContent = proposedContent,
        fingerprint = fingerprint
    )
    return library.replace(
        document.copy(proposal = proposal, lastDirectiveFingerprint = fingerprint)
    ) to document.id
}

private fun existingDocumentForDirectives(
    library: CanvasLibrary,
    chatRoomId: Int,
    directives: List<CanvasDirective>
): CanvasDocument? {
    val id = directives.lastOrNull { it.id != null }?.id
    return id?.let { documentId ->
        library.documents.firstOrNull { it.id == documentId && it.chatRoomId == chatRoomId }
    } ?: library.latestFor(chatRoomId)
}

private fun newDocument(
    chatRoomId: Int,
    title: String,
    kind: CanvasKind,
    language: String,
    content: String,
    source: CanvasVersionSource,
    now: Long,
    newId: () -> String
): CanvasDocument {
    val documentId = newId()
    val version = CanvasVersion(
        id = newId(),
        content = content,
        title = title,
        createdAt = now,
        source = source
    )
    return CanvasDocument(
        id = documentId,
        chatRoomId = chatRoomId,
        title = title,
        kind = kind,
        language = language,
        updatedAt = now,
        versions = listOf(version)
    )
}

private fun trimVersions(versions: List<CanvasVersion>): List<CanvasVersion> {
    if (versions.size <= CanvasLimits.MAX_VERSIONS) return versions
    return versions.takeLast(CanvasLimits.MAX_VERSIONS)
}

private fun rememberFingerprint(library: CanvasLibrary, fingerprint: String): CanvasLibrary {
    if (fingerprint in library.appliedFingerprints) return library
    val fingerprints = (library.appliedFingerprints + fingerprint).takeLast(CanvasLimits.MAX_FINGERPRINTS)
    return library.copy(appliedFingerprints = fingerprints)
}

private fun CanvasLibrary.replace(document: CanvasDocument): CanvasLibrary = copy(
    documents = documents.map { current -> if (current.id == document.id) document else current }
)

private fun CanvasLibrary.latestFor(chatRoomId: Int): CanvasDocument? =
    documents.filter { it.chatRoomId == chatRoomId }.maxByOrNull { it.updatedAt }
