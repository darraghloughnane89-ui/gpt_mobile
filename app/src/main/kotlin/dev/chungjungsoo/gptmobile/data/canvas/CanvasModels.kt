package dev.chungjungsoo.gptmobile.data.canvas

import kotlinx.serialization.Serializable

object CanvasLimits {
    const val MAX_CONTENT_CHARS = 200_000
    const val MAX_VERSIONS = 40
    const val MAX_TITLE_CHARS = 80
    const val MAX_FINGERPRINTS = 400
    const val COALESCE_USER_EDITS_MS = 2_000L
    const val PROMPT_EXCERPT_CHARS = 12_000
}

enum class CanvasKind {
    Markdown,
    Code
}

enum class CanvasVersionSource {
    Open,
    User,
    Model
}

enum class CanvasAction {
    Open,
    Propose,
    ProposeSelection
}

enum class CanvasExportFormat {
    Markdown,
    PlainText,
    Pdf
}

@Serializable
data class CanvasVersion(
    val id: String,
    val content: String,
    val title: String,
    val createdAt: Long,
    val source: CanvasVersionSource
)

@Serializable
data class PendingSelection(
    val start: Int,
    val end: Int,
    val text: String
)

@Serializable
data class CanvasProposal(
    val baseVersionId: String,
    val proposedContent: String,
    val fingerprint: String
)

@Serializable
data class CanvasDocument(
    val id: String,
    val chatRoomId: Int,
    val title: String,
    val kind: CanvasKind,
    val language: String,
    val updatedAt: Long,
    val versions: List<CanvasVersion>,
    val proposal: CanvasProposal? = null,
    val pendingSelection: PendingSelection? = null,
    val lastDirectiveFingerprint: String? = null
) {
    val content: String
        get() = versions.lastOrNull()?.content.orEmpty()

    val activeVersionId: String
        get() = versions.lastOrNull()?.id.orEmpty()
}

@Serializable
data class CanvasLibrary(
    val documents: List<CanvasDocument> = emptyList(),
    val appliedFingerprints: List<String> = emptyList()
)

data class CanvasDirective(
    val fingerprint: String,
    val id: String?,
    val title: String?,
    val kind: CanvasKind?,
    val language: String?,
    val action: CanvasAction,
    val body: String
)

data class CanvasSeed(
    val title: String,
    val kind: CanvasKind,
    val language: String,
    val content: String
)

sealed class AssistantCanvasPlan {
    data class Directives(val directives: List<CanvasDirective>) : AssistantCanvasPlan()
    data class Seed(val seed: CanvasSeed) : AssistantCanvasPlan()
    data class WholeMarkdown(val content: String) : AssistantCanvasPlan()
    data object PreviewOwned : AssistantCanvasPlan()
    data object Empty : AssistantCanvasPlan()
}

sealed class OpenCanvasResult {
    data class Opened(val document: CanvasDocument) : OpenCanvasResult()
    data object PreviewOwned : OpenCanvasResult()
    data object Empty : OpenCanvasResult()
}

data class CanvasDiffLine(
    val kind: CanvasDiffKind,
    val text: String
)

enum class CanvasDiffKind {
    Same,
    Added,
    Removed
}
