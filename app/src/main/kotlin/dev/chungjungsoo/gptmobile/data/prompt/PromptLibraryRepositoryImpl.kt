package dev.chungjungsoo.gptmobile.data.prompt

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PromptLibraryRepositoryImpl(
    private val storage: PromptLibraryStorage,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : PromptLibraryRepository {
    private val mutex = Mutex()
    private val prompts = MutableStateFlow<List<SavedPrompt>>(emptyList())
    private var loaded = false

    override fun observe(): Flow<List<SavedPrompt>> = flow {
        ensureLoaded()
        emitAll(prompts)
    }

    override suspend fun save(prompt: SavedPrompt): SavedPrompt = mutex.withLock {
        ensureLoadedLocked()
        val now = clock()
        val cleaned = sanitizeImportedPrompt(
            prompt.copy(id = prompt.id.ifBlank { newId() })
        ) ?: throw IllegalArgumentException("A prompt needs a title and body.")
        val current = prompts.value
        val existing = current.firstOrNull { it.id == cleaned.id }
        val stored = cleaned.copy(
            createdAt = existing?.createdAt ?: cleaned.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now
        )
        val next = libraryOrder(
            if (existing == null) current + stored else current.map { if (it.id == stored.id) stored else it }
        )
        persist(next)
        stored
    }

    override suspend fun delete(id: String) {
        mutex.withLock {
            ensureLoadedLocked()
            persist(prompts.value.filterNot { it.id == id })
        }
    }

    override suspend fun setFavourite(id: String, favourite: Boolean) {
        mutex.withLock {
            ensureLoadedLocked()
            val next = prompts.value.map { prompt ->
                if (prompt.id == id) prompt.copy(favourite = favourite, updatedAt = clock()) else prompt
            }
            persist(libraryOrder(next))
        }
    }

    override suspend fun importJson(raw: String): PromptImportOutcome {
        val document = PromptLibraryCodec.decode(raw) ?: return PromptImportOutcome.Unreadable
        if (document.version > PromptLibraryDocument.CURRENT_VERSION) return PromptImportOutcome.Unreadable
        return mutex.withLock {
            ensureLoadedLocked()
            val current = prompts.value.associateBy { it.id }.toMutableMap()
            var added = 0
            var updated = 0
            var skipped = 0
            document.prompts.forEach { incoming ->
                val clean = sanitizeImportedPrompt(incoming)
                if (clean == null) {
                    skipped += 1
                    return@forEach
                }
                if (current.size >= PromptLibraryCodec.MAX_PROMPTS && clean.id !in current) {
                    skipped += 1
                    return@forEach
                }
                if (clean.id in current) updated += 1 else added += 1
                val previous = current[clean.id]
                current[clean.id] = clean.copy(
                    createdAt = previous?.createdAt ?: clean.createdAt,
                    updatedAt = clock()
                )
            }
            persist(libraryOrder(current.values.toList()))
            PromptImportOutcome.Success(PromptImportCounts(added, updated, skipped))
        }
    }

    override suspend fun exportJson(): String {
        ensureLoaded()
        return PromptLibraryCodec.encode(prompts.value)
    }

    private suspend fun ensureLoaded() {
        mutex.withLock { ensureLoadedLocked() }
    }

    private suspend fun ensureLoadedLocked() {
        if (loaded) return
        val raw = storage.read()
        val decoded = if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            PromptLibraryCodec.decode(raw)?.prompts.orEmpty().mapNotNull(::sanitizeImportedPrompt)
        }
        prompts.value = libraryOrder(decoded.distinctBy { it.id }.take(PromptLibraryCodec.MAX_PROMPTS))
        loaded = true
    }

    private suspend fun persist(next: List<SavedPrompt>) {
        val ordered = libraryOrder(next).take(PromptLibraryCodec.MAX_PROMPTS)
        storage.write(PromptLibraryCodec.encode(ordered))
        prompts.value = ordered
    }
}
