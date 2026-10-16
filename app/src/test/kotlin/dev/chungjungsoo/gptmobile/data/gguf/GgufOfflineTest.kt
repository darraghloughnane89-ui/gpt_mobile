package dev.chungjungsoo.gptmobile.data.gguf

import dev.chungjungsoo.gptmobile.data.agent.ProviderEvent
import dev.chungjungsoo.gptmobile.data.agent.provider.LlamaCppAdapter
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufOfflineTest {
    @Test
    fun `low memory refuses large files and clamps context`() {
        val refused = GgufRamPolicy.assess(fileBytes = 600, totalMemBytes = 1_000)
        assertEquals(RamFit.REFUSE, refused.fit)

        val warned = GgufRamPolicy.assess(fileBytes = 400, totalMemBytes = 1_000)
        assertEquals(RamFit.WARN, warned.fit)

        val ok = GgufRamPolicy.assess(fileBytes = 100, totalMemBytes = 1_000)
        assertEquals(RamFit.OK, ok.fit)
        assertEquals(null, ok.detail)

        val unknown = GgufRamPolicy.assess(fileBytes = 10, totalMemBytes = 0)
        assertEquals(RamFit.WARN, unknown.fit)

        assertEquals(2048, GgufRamPolicy.recommendedContext(2_000_000_000L))
        assertEquals(GgufRamPolicy.DEFAULT_CONTEXT, GgufRamPolicy.recommendedContext(4_000_000_000L))
        assertEquals(8192, GgufRamPolicy.recommendedContext(8_000_000_000L))
        assertEquals(GgufRamPolicy.DEFAULT_CONTEXT, GgufRamPolicy.clampContext(100_000, 2_000_000_000L))
        assertEquals(GgufRamPolicy.MIN_CONTEXT, GgufRamPolicy.clampContext(8, 8_000_000_000L))
    }

    @Test
    fun `store imports lists updates and deletes a gguf`() = runBlocking {
        val root = tempRoot()
        val store = GgufModelStore(root)
        val imported = store.importStream(
            displayName = "Tiny",
            sourceLabel = "tiny.gguf",
            input = ByteArrayInputStream(ggufBytes(contextLength = 100_000)),
            sizeHint = 32,
            totalMemBytes = 8_000_000_000L
        )
        assertEquals(GgufRamPolicy.MAX_CONTEXT, imported.contextSize)

        assertEquals(listOf(imported.id), store.list().map { it.id })
        val rejected = runCatching {
            store.importStream(
                displayName = "Not a model",
                sourceLabel = "notes.txt",
                input = ByteArrayInputStream(ByteArray(16) { 7 }),
                sizeHint = 16,
                totalMemBytes = 8_000_000_000L
            )
        }.exceptionOrNull()
        assertTrue(rejected?.message?.contains("GGUF") == true)
        assertTrue(store.fileFor(imported).isFile)
        val updated = store.updateParameters(imported.id, contextSize = 100_000, temperature = 9f, totalMemBytes = 2_000_000_000L)
        assertEquals(GgufRamPolicy.DEFAULT_CONTEXT, updated?.contextSize)
        assertEquals(2f, updated?.temperature)
        store.delete(imported.id)
        assertTrue(store.list().isEmpty())
        assertFalse(store.fileFor(imported).exists())
        root.deleteRecursively()
        Unit
    }

    @Test
    fun `store refuses a file that cannot fit in memory`() = runBlocking {
        val root = tempRoot()
        val store = GgufModelStore(root)
        val error = runCatching {
            store.importStream(
                displayName = "Huge",
                sourceLabel = "huge.gguf",
                input = ByteArrayInputStream(ggufBytes(padTo = 80)),
                sizeHint = 80,
                totalMemBytes = 100
            )
        }.exceptionOrNull()

        assertTrue(error != null)
        assertTrue(store.list().isEmpty())
        root.deleteRecursively()
        Unit
    }

    @Test
    fun `metadata reads llama context length and rejects other files`() {
        val file = File(tempRoot(), "model.gguf")
        file.writeBytes(ggufBytes(contextLength = 8192))
        assertTrue(GgufMetadata.isGguf(file))
        assertEquals(8192L, GgufMetadata.contextLength(file))
        file.writeBytes(ByteArray(16) { 1 })
        assertFalse(GgufMetadata.isGguf(file))
        assertEquals(null, GgufMetadata.contextLength(file))
        file.parentFile?.deleteRecursively()
    }

    @Test
    fun `downloader rejects non http urls`() = runBlocking {
        val downloader = GgufDownloader(HttpClient(CIO))
        val destination = File(tempRoot(), "model.gguf")
        val error = runCatching {
            downloader.download("file:///tmp/model.gguf", destination) { _, _ -> }
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        destination.parentFile?.deleteRecursively()
        Unit
    }

    @Test
    fun `adapter streams text then completes`() = runBlocking {
        val (store, record) = importedModel()
        val runtime = RecordingRuntime(tokens = listOf("Hello", " world"))
        val adapter = adapter(store, runtime, totalMemBytes = 8_000_000_000L)

        val events = adapter.openSession(turns("hi"), platform(record.id)).streamRound(emptyList(), emptyList()).toList()

        assertEquals(listOf("Hello", " world"), events.filterIsInstance<ProviderEvent.TextDelta>().map { it.text })
        assertTrue(events.last() is ProviderEvent.Completed)
        assertEquals(1, runtime.calls)
        storeRoot(store).deleteRecursively()
        Unit
    }

    @Test
    fun `adapter reports a missing model without loading it`() = runBlocking {
        val root = tempRoot()
        val runtime = RecordingRuntime()
        val adapter = adapter(GgufModelStore(root), runtime, totalMemBytes = 8_000_000_000L)

        val events = adapter.openSession(turns("hi"), platform("missing")).streamRound(emptyList(), emptyList()).toList()

        assertTrue(events.single() is ProviderEvent.Failed)
        assertEquals(0, runtime.calls)
        root.deleteRecursively()
        Unit
    }

    @Test
    fun `adapter refuses an oversized model before generation`() = runBlocking {
        val (store, record) = importedModel(bytes = 80)
        val runtime = RecordingRuntime()
        val adapter = adapter(store, runtime, totalMemBytes = 100)

        val events = adapter.openSession(turns("hi"), platform(record.id)).streamRound(emptyList(), emptyList()).toList()

        assertTrue(events.single() is ProviderEvent.Failed)
        assertEquals(0, runtime.calls)
    }

    @Test
    fun `adapter notices image attachments and still generates text`() = runBlocking {
        val (store, record) = importedModel()
        val runtime = RecordingRuntime(tokens = listOf("ok"))
        val adapter = adapter(store, runtime, totalMemBytes = 8_000_000_000L)
        val attachment = ChatAttachment(
            localFilePath = "/tmp/photo.jpg",
            preparedFilePath = "/tmp/photo.jpg",
            displayName = "photo.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 10
        )
        val turn = ConversationTurn(
            userMessage = MessageV2(content = "look", platformType = null, attachments = listOf(attachment)),
            assistantMessage = null,
            isCurrentTurn = true
        )

        val events = adapter.openSession(listOf(turn), platform(record.id)).streamRound(emptyList(), emptyList()).toList()

        assertTrue(events.any { it is ProviderEvent.Notice && it.message == LlamaCppAdapter.DEFAULT_ATTACHMENTS })
        assertTrue(events.any { it is ProviderEvent.Completed })
    }

    @Test
    fun `adapter cancellation stops before completion`() = runBlocking {
        val (store, record) = importedModel()
        val runtime = object : GgufRuntime {
            override val isBusy: Boolean = false
            override fun stream(
                modelPath: String,
                systemPrompt: String?,
                messages: List<GgufChatTurn>,
                sampling: GgufSampling
            ): Flow<String> = flow {
                emit("partial")
                awaitCancellation()
            }
        }
        val adapter = adapter(store, runtime, totalMemBytes = 8_000_000_000L)
        val events = mutableListOf<ProviderEvent>()
        val job = launch {
            adapter.openSession(turns("hi"), platform(record.id)).streamRound(emptyList(), emptyList()).collect { event ->
                events += event
            }
        }
        while (events.none { it is ProviderEvent.TextDelta }) {
            yield()
        }
        job.cancel()
        val cancelled = runCatching { job.join() }.exceptionOrNull()
        assertTrue(cancelled == null || cancelled is CancellationException)
        assertTrue(events.any { it is ProviderEvent.TextDelta && it.text == "partial" })
        assertTrue(events.none { it is ProviderEvent.Completed })
    }

    private fun importedModel(bytes: Int = 32): Pair<GgufModelStore, GgufModelRecord> = runBlocking {
        val store = GgufModelStore(tempRoot())
        val record = store.importStream(
            displayName = "Tiny",
            sourceLabel = "tiny.gguf",
            input = ByteArrayInputStream(ggufBytes(padTo = bytes)),
            sizeHint = bytes.toLong(),
            totalMemBytes = 8_000_000_000L
        )
        store to record
    }

    private fun storeRoot(store: GgufModelStore): File {
        val record = runBlocking { store.list().single() }
        return store.fileFor(record).parentFile!!.parentFile!!
    }

    private fun adapter(store: GgufModelStore, runtime: GgufRuntime, totalMemBytes: Long) = LlamaCppAdapter(
        models = store,
        runtime = runtime,
        memory = object : DeviceMemory {
            override fun totalBytes(): Long = totalMemBytes
            override fun availableBytes(): Long = totalMemBytes
        }
    )

    private fun platform(modelId: String) = PlatformV2(
        uid = "gguf",
        name = "GGUF",
        compatibleType = ClientType.LLAMA_CPP,
        apiUrl = "",
        model = modelId,
        temperature = 0.4f,
        maxTokens = 64
    )

    private fun turns(text: String) = listOf(
        ConversationTurn(
            userMessage = MessageV2(content = text, platformType = null),
            assistantMessage = null,
            isCurrentTurn = true
        )
    )

    private fun tempRoot(): File = File(System.getProperty("java.io.tmpdir"), "gguf-test-${System.nanoTime()}").apply { mkdirs() }

    private fun ggufBytes(contextLength: Long = 4096, padTo: Int = 64): ByteArray {
        val body = ByteArrayOutputStream()
        body.writeU32(0x46554747L)
        body.writeU32(3L)
        body.writeU64(0L)
        body.writeU64(1L)
        val key = "llama.context_length".toByteArray(Charsets.UTF_8)
        body.writeU64(key.size.toLong())
        body.write(key)
        body.writeU32(4L)
        body.writeU32(contextLength)
        val raw = body.toByteArray()
        if (raw.size >= padTo) return raw
        return raw + ByteArray(padTo - raw.size)
    }

    private fun ByteArrayOutputStream.writeU32(value: Long) {
        write((value and 0xff).toInt())
        write((value shr 8 and 0xff).toInt())
        write((value shr 16 and 0xff).toInt())
        write((value shr 24 and 0xff).toInt())
    }

    private fun ByteArrayOutputStream.writeU64(value: Long) {
        var remaining = value
        repeat(8) {
            write((remaining and 0xff).toInt())
            remaining = remaining ushr 8
        }
    }

    private class RecordingRuntime(
        private val tokens: List<String> = emptyList()
    ) : GgufRuntime {
        override var isBusy: Boolean = false
        var calls: Int = 0

        override fun stream(
            modelPath: String,
            systemPrompt: String?,
            messages: List<GgufChatTurn>,
            sampling: GgufSampling
        ): Flow<String> = flow {
            calls += 1
            tokens.forEach { emit(it) }
        }
    }
}
