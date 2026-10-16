package dev.chungjungsoo.gptmobile.presentation.ui.canvas

import dev.chungjungsoo.gptmobile.data.canvas.CanvasDocument
import dev.chungjungsoo.gptmobile.data.canvas.CanvasLimits
import dev.chungjungsoo.gptmobile.data.canvas.CanvasRepository
import dev.chungjungsoo.gptmobile.data.canvas.OpenCanvasResult
import dev.chungjungsoo.gptmobile.data.canvas.PendingSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CanvasChatController(
    private val repository: CanvasRepository
) {
    private val _document = MutableStateFlow<CanvasDocument?>(null)
    val document = _document.asStateFlow()

    private val _documents = MutableStateFlow<List<CanvasDocument>>(emptyList())
    val documents = _documents.asStateFlow()

    private val _chatOpen = MutableStateFlow(false)
    val chatOpen = _chatOpen.asStateFlow()

    private val _selection = MutableStateFlow<PendingSelection?>(null)
    val selection = _selection.asStateFlow()

    private var chatId: Int = 0
    private var ownsUnsaved: Boolean = false
    private var reassignFromZero: Boolean = false

    fun bind(chatRoomId: Int) {
        if (chatId == 0 && chatRoomId > 0 && ownsUnsaved) {
            reassignFromZero = true
        }
        chatId = chatRoomId
    }

    fun peekSelection(): PendingSelection? = _selection.value

    fun close() {
        _document.value = null
        _chatOpen.value = false
        _selection.value = null
    }

    fun setChatOpen(open: Boolean) {
        _chatOpen.value = open
    }

    fun clearSelection() {
        _selection.value = null
    }

    suspend fun refreshRoom() {
        if (reassignFromZero && chatId > 0) {
            repository.reassignChat(0, chatId)
            reassignFromZero = false
            ownsUnsaved = false
        }
        val docs = if (chatId > 0 || ownsUnsaved) repository.documents(chatId) else emptyList()
        _documents.value = docs
        _document.value = _document.value?.id?.let { id -> docs.firstOrNull { it.id == id } }
    }

    suspend fun createBlank(title: String): CanvasDocument {
        if (chatId == 0) ownsUnsaved = true
        val created = repository.createBlank(chatId, title)
        show(created)
        return created
    }

    suspend fun openFromAssistant(content: String, untitled: String): OpenCanvasResult {
        if (chatId == 0) ownsUnsaved = true
        return when (val result = repository.openFromAssistant(chatId, content, untitled)) {
            is OpenCanvasResult.Opened -> {
                show(result.document)
                result
            }

            OpenCanvasResult.Empty, OpenCanvasResult.PreviewOwned -> result
        }
    }

    suspend fun onAssistantReplies(olderMessages: List<String>, latestMessages: List<String>): String? {
        if (chatId == 0 && !ownsUnsaved && olderMessages.isEmpty() && latestMessages.isEmpty()) return null
        val focused = repository.ingest(chatId, olderMessages, latestMessages)
        if (focused != null) {
            repository.document(focused)?.let { show(it) }
            _chatOpen.value = false
        } else {
            refreshListedDocuments()
        }
        return focused
    }

    suspend fun openDocument(id: String) {
        val document = repository.document(id) ?: return
        show(document)
    }

    suspend fun updateContent(content: String): Boolean {
        val current = _document.value ?: return true
        if (content.length > CanvasLimits.MAX_CONTENT_CHARS) return false
        if (current.proposal != null || content == current.content) return true
        val updated = repository.editContent(current.id, content) ?: return true
        _document.value = updated
        refreshListedDocuments()
        return true
    }

    suspend fun updateTitle(title: String) {
        val id = _document.value?.id ?: return
        repository.editTitle(id, title)?.let { updated ->
            _document.value = updated
            refreshListedDocuments()
        }
    }

    suspend fun armSelection(start: Int, end: Int, text: String) {
        val id = _document.value?.id ?: return
        if (text.isBlank()) return
        val selection = PendingSelection(start = start, end = end, text = text)
        _selection.value = selection
        repository.rememberSelection(id, selection)?.let { updated ->
            _document.value = updated
        }
        _chatOpen.value = true
    }

    suspend fun acceptProposal() {
        val id = _document.value?.id ?: return
        repository.acceptProposal(id)?.let { updated ->
            _document.value = updated
            _selection.value = null
            refreshListedDocuments()
        }
    }

    suspend fun rejectProposal() {
        val id = _document.value?.id ?: return
        repository.rejectProposal(id)?.let { updated ->
            _document.value = updated
            _selection.value = null
        }
    }

    suspend fun restoreVersion(versionId: String) {
        val id = _document.value?.id ?: return
        repository.restoreVersion(id, versionId)?.let { updated ->
            _document.value = updated
            _selection.value = null
            refreshListedDocuments()
        }
    }

    suspend fun clearStoredSelection() {
        val id = _document.value?.id ?: return
        _selection.value = null
        repository.clearSelection(id)?.let { updated ->
            _document.value = updated
        }
    }

    private suspend fun show(document: CanvasDocument) {
        _document.value = document
        _selection.value = document.pendingSelection
        refreshListedDocuments()
    }

    private suspend fun refreshListedDocuments() {
        _documents.value = repository.documents(chatId)
    }
}
