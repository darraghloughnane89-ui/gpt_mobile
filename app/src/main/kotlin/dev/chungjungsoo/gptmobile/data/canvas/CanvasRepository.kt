package dev.chungjungsoo.gptmobile.data.canvas

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class CanvasRepository @Inject constructor(
    private val store: CanvasStore
) {
    private val mutex = Mutex()
    private var library = CanvasLibrary()
    private var loaded = false

    suspend fun documents(chatRoomId: Int): List<CanvasDocument> = read().documents
        .filter { it.chatRoomId == chatRoomId }
        .sortedByDescending { it.updatedAt }

    suspend fun document(id: String): CanvasDocument? = read().documents.firstOrNull { it.id == id }

    suspend fun createBlank(chatRoomId: Int, title: String): CanvasDocument = mutate { current ->
        createCanvas(
            library = current,
            chatRoomId = chatRoomId,
            title = title,
            kind = CanvasKind.Markdown,
            language = "markdown",
            content = "",
            source = CanvasVersionSource.Open,
            now = now(),
            newId = ::newId
        )
    }

    suspend fun openFromAssistant(chatRoomId: Int, content: String, untitled: String): OpenCanvasResult =
        mutate { current ->
            openCanvasFromAssistant(
                library = current,
                chatRoomId = chatRoomId,
                content = content,
                now = now(),
                newId = ::newId,
                untitled = untitled
            )
        }

    suspend fun ingest(
        chatRoomId: Int,
        olderMessages: List<String>,
        latestMessages: List<String>
    ): String? = mutate { current ->
        ingestCanvasMessages(
            library = current,
            chatRoomId = chatRoomId,
            olderMessages = olderMessages,
            latestMessages = latestMessages,
            now = now(),
            newId = ::newId
        )
    }

    suspend fun editContent(documentId: String, content: String): CanvasDocument? = mutate { current ->
        val next = editCanvasContent(current, documentId, content, now(), ::newId)
        next to next.documents.firstOrNull { it.id == documentId }
    }

    suspend fun editTitle(documentId: String, title: String): CanvasDocument? = mutate { current ->
        val next = editCanvasTitle(current, documentId, title, now())
        next to next.documents.firstOrNull { it.id == documentId }
    }

    suspend fun rememberSelection(documentId: String, selection: PendingSelection): CanvasDocument? =
        mutate { current ->
            val next = rememberCanvasSelection(current, documentId, selection)
            next to next.documents.firstOrNull { it.id == documentId }
        }

    suspend fun clearSelection(documentId: String): CanvasDocument? = mutate { current ->
        val next = clearCanvasSelection(current, documentId)
        next to next.documents.firstOrNull { it.id == documentId }
    }

    suspend fun acceptProposal(documentId: String): CanvasDocument? = mutate { current ->
        val next = acceptCanvasProposal(current, documentId, now(), ::newId)
        next to next.documents.firstOrNull { it.id == documentId }
    }

    suspend fun rejectProposal(documentId: String): CanvasDocument? = mutate { current ->
        val next = rejectCanvasProposal(current, documentId)
        next to next.documents.firstOrNull { it.id == documentId }
    }

    suspend fun restoreVersion(documentId: String, versionId: String): CanvasDocument? = mutate { current ->
        val next = restoreCanvasVersion(current, documentId, versionId, now(), ::newId)
        next to next.documents.firstOrNull { it.id == documentId }
    }

    suspend fun reassignChat(fromChatRoomId: Int, toChatRoomId: Int) {
        mutate { current ->
            reassignCanvasChat(current, fromChatRoomId, toChatRoomId) to Unit
        }
    }

    private suspend fun read(): CanvasLibrary = mutex.withLock {
        ensureLoaded()
        library
    }

    private suspend fun <T> mutate(block: (CanvasLibrary) -> Pair<CanvasLibrary, T>): T = mutex.withLock {
        ensureLoaded()
        val (next, result) = block(library)
        if (next != library) {
            library = next
            store.save(library)
        }
        result
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        library = store.load()
        loaded = true
    }

    private fun newId(): String = UUID.randomUUID().toString()

    private fun now(): Long = System.currentTimeMillis()
}
