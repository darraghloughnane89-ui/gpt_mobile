package dev.chungjungsoo.gptmobile.data.gguf

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.work.Data
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufModelManagerTest {
    @Test
    fun `catalog lists verified pixel-sized chat ggufs`() {
        val entries = GgufCatalog.entries
        assertEquals(7, entries.size)
        assertEquals(setOf("Qwen", "Llama", "Gemma"), entries.map { it.family }.toSet())
        assertTrue(entries.all { it.quantization == "Q4_K_M" })
        assertEquals(entries.map { it.id }.toSet().size, entries.size)
        val expected = mapOf(
            "Qwen/Qwen2.5-1.5B-Instruct-GGUF" to Verified(
                fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                sizeBytes = 1_117_320_736L,
                sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
                licenseName = "Apache-2.0"
            ),
            "Qwen/Qwen2.5-3B-Instruct-GGUF" to Verified(
                fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
                sizeBytes = 2_104_932_768L,
                sha256 = "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d",
                licenseName = "Qwen Research License"
            ),
            "Qwen/Qwen3-4B-GGUF" to Verified(
                fileName = "Qwen3-4B-Q4_K_M.gguf",
                sizeBytes = 2_497_280_256L,
                sha256 = "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5",
                licenseName = "Apache-2.0"
            ),
            "bartowski/Llama-3.2-1B-Instruct-GGUF" to Verified(
                fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                sizeBytes = 807_694_464L,
                sha256 = "6f85a640a97cf2bf5b8e764087b1e83da0fdb51d7c9fab7d0fece9385611df83",
                licenseName = "Llama 3.2 Community License"
            ),
            "bartowski/Llama-3.2-3B-Instruct-GGUF" to Verified(
                fileName = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                sizeBytes = 2_019_377_696L,
                sha256 = "6c1a2b41161032677be168d354123594c0e6e67d2b9227c84f296ad037c728ff",
                licenseName = "Llama 3.2 Community License"
            ),
            "unsloth/gemma-3-1b-it-GGUF" to Verified(
                fileName = "gemma-3-1b-it-Q4_K_M.gguf",
                sizeBytes = 806_058_272L,
                sha256 = "8270790f3ab69fdfe860b7b64008d9a19986d8df7e407bb018184caa08798ebd",
                licenseName = "Gemma Terms of Use"
            ),
            "unsloth/gemma-3-4b-it-GGUF" to Verified(
                fileName = "gemma-3-4b-it-Q4_K_M.gguf",
                sizeBytes = 2_489_894_016L,
                sha256 = "04a43a22e8d2003deda5acc262f68ec1005fa76c735a9962a8c77042a74a7d19",
                licenseName = "Gemma Terms of Use"
            )
        )
        entries.forEach { entry ->
            val verified = expected.getValue(entry.repoId)
            assertEquals(verified.fileName, entry.fileName)
            assertEquals(verified.sizeBytes, entry.sizeBytes)
            assertEquals(verified.sha256, entry.sha256)
            assertEquals(verified.licenseName, entry.licenseName)
            assertEquals("https://huggingface.co/${entry.repoId}/resolve/main/${entry.fileName}", entry.downloadUrl)
            assertTrue(entry.licenseUrl.startsWith("https://"))
            assertTrue(entry.sizeBytes in 700_000_000L..2_600_000_000L)
            assertTrue(entry.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(GgufPartialFiles.isSafeKey(entry.id))
        }
    }

    @Test
    fun `hugging face url accepts blob and resolve gguf links`() {
        val blob = HuggingFaceGgufUrl.parse(
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/blob/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true"
        )
        assertEquals(
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            blob?.downloadUrl
        )
        assertEquals(
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/raw/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            blob?.pointerUrl
        )
        val nested = HuggingFaceGgufUrl.parse(
            "https://www.huggingface.co/org/repo/resolve/abc123/subdir/model.gguf"
        )
        assertEquals("https://huggingface.co/org/repo/resolve/abc123/subdir/model.gguf", nested?.downloadUrl)
        assertEquals("model.gguf", nested?.fileName)
        assertNull(HuggingFaceGgufUrl.parse("https://example.com/model.gguf"))
        assertNull(HuggingFaceGgufUrl.parse("http://huggingface.co/org/repo/resolve/main/model.gguf"))
        assertNull(HuggingFaceGgufUrl.parse("https://huggingface.co/org/repo/blob/main/README.md"))
        assertNull(HuggingFaceGgufUrl.parse("https://huggingface.co/org/repo"))
    }

    @Test
    fun `lfs pointer reads published size and sha256`() {
        val parsed = GgufLfsPointer.parse(
            """
            version https://git-lfs.github.com/spec/v1
            oid sha256:6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e
            size 1117320736
            """.trimIndent()
        )
        assertEquals(1_117_320_736L, parsed?.sizeBytes)
        assertEquals("6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e", parsed?.sha256)
        assertNull(GgufLfsPointer.parse("not a pointer"))
    }

    @Test
    fun `gate blocks storage and oversized ram before download`() {
        val refused = GgufDownloadGate.evaluate(
            fileBytes = 600,
            availableStorageBytes = 10_000,
            totalMemBytes = 1_000,
            storageMarginBytes = 0
        )
        assertTrue(refused is GgufDownloadDecision.Blocked)

        val storage = GgufDownloadGate.evaluate(
            fileBytes = 1_000,
            availableStorageBytes = 1_000,
            totalMemBytes = 16_000_000_000L,
            storageMarginBytes = 64
        )
        assertEquals(GgufDownloadMessages.STORAGE, (storage as GgufDownloadDecision.Blocked).message)

        val resumed = GgufDownloadGate.evaluate(
            fileBytes = 1_000,
            availableStorageBytes = 0,
            totalMemBytes = 16_000_000_000L,
            partialBytes = 1_000,
            storageMarginBytes = 64
        )
        assertTrue(resumed is GgufDownloadDecision.Allowed)

        val warn = GgufDownloadGate.evaluate(
            fileBytes = 400,
            availableStorageBytes = 10_000,
            totalMemBytes = 1_000,
            storageMarginBytes = 0
        )
        assertTrue(warn is GgufDownloadDecision.Confirm)
        val accepted = GgufDownloadGate.evaluate(
            fileBytes = 400,
            availableStorageBytes = 10_000,
            totalMemBytes = 1_000,
            storageMarginBytes = 0,
            acceptWarning = true
        )
        assertTrue(accepted is GgufDownloadDecision.Allowed)
    }

    @Test
    fun `sha256 matches the file and rejects a different digest`() {
        val root = tempRoot()
        val file = File(root, "model.bin")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        val digest = GgufSha256.hexOf(file)
        assertTrue(GgufSha256.matches(file, digest.uppercase()))
        assertFalse(GgufSha256.matches(file, "a".repeat(64)))
        assertFalse(GgufSha256.matches(file, "short"))
        root.deleteRecursively()
    }

    @Test
    fun `work input roundtrips a download request`() {
        val request = GgufCatalog.entries.first().toDownloadRequest()
        val restored = GgufDownloadInput.from(GgufDownloadInput.toData(request))
        assertEquals(request, restored)
        assertEquals(null, GgufDownloadInput.from(Data.Builder().putString(GgufDownloadInput.KEY_URL, "file:///tmp/x.gguf").build()))
    }

    @Test
    fun `coordinator enqueues a catalog model and confirms a ram warning`() = runBlocking {
        val root = tempRoot()
        val store = GgufModelStore(root)
        val scheduler = FakeScheduler()
        val coordinator = coordinator(
            store = store,
            scheduler = scheduler,
            storageBytes = 20_000_000_000L,
            totalMemBytes = 16_000_000_000L
        )
        val started = coordinator.requestCatalogDownload("qwen2.5-1.5b-instruct-q4km")
        assertTrue(started is GgufStartResult.Started)
        assertEquals(
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            scheduler.enqueued.single().url
        )
        assertEquals(
            "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
            scheduler.enqueued.single().sha256
        )

        scheduler.enqueued.clear()
        scheduler.active.clear()
        val tight = coordinator(
            store = store,
            scheduler = scheduler,
            storageBytes = 200_000_000L,
            totalMemBytes = 1_000,
            metadata = FakeMetadata(GgufPublishedMetadata(sizeBytes = 400, sha256 = null))
        )
        val pasted = "https://huggingface.co/org/repo/resolve/main/model.gguf"
        val warning = tight.requestPastedUrl(pasted)
        assertTrue(warning is GgufStartResult.NeedsConfirmation)
        assertTrue(scheduler.enqueued.isEmpty())
        tight.dismissPending()
        assertTrue(tight.confirmPending() is GgufStartResult.Rejected)
        val again = tight.requestPastedUrl(pasted)
        assertTrue(again is GgufStartResult.NeedsConfirmation)
        assertTrue(tight.confirmPending() is GgufStartResult.Started)
        assertEquals(1, scheduler.enqueued.size)
        root.deleteRecursively()
        Unit
    }

    @Test
    fun `coordinator rejects a download that cannot fit`() = runBlocking {
        val root = tempRoot()
        val scheduler = FakeScheduler()
        val coordinator = coordinator(
            store = GgufModelStore(root),
            scheduler = scheduler,
            storageBytes = 32L * 1024L * 1024L,
            totalMemBytes = 16_000_000_000L
        )
        val rejected = coordinator.requestCatalogDownload("qwen3-4b-q4km")
        assertTrue(rejected is GgufStartResult.Rejected)
        assertTrue((rejected as GgufStartResult.Rejected).message.contains("storage"))
        assertTrue(scheduler.enqueued.isEmpty())
        root.deleteRecursively()
        Unit
    }

    @Test
    fun `pasted hugging face url uses published metadata`() = runBlocking {
        val root = tempRoot()
        val scheduler = FakeScheduler()
        val metadata = FakeMetadata(
            GgufPublishedMetadata(
                sizeBytes = 111L,
                sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
            )
        )
        val coordinator = coordinator(
            store = GgufModelStore(root),
            scheduler = scheduler,
            storageBytes = 200_000_000L,
            totalMemBytes = 16_000_000_000L,
            metadata = metadata
        )
        val started = coordinator.requestPastedUrl(
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/blob/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
        )
        assertTrue(started is GgufStartResult.Started)
        val request = scheduler.enqueued.single()
        assertEquals(
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            request.url
        )
        assertEquals(metadata.value.sha256, request.sha256)
        assertTrue(GgufPartialFiles.isSafeKey(request.downloadKey))

        val rejected = coordinator.requestPastedUrl("https://example.com/model.gguf")
        assertEquals(GgufDownloadMessages.NOT_A_GGUF_URL, (rejected as GgufStartResult.Rejected).message)
        root.deleteRecursively()
        Unit
    }

    @Test
    fun `default offline model is stored and cleared when the file is deleted`() = runBlocking {
        val root = tempRoot()
        val store = GgufModelStore(root)
        val imported = store.importStream(
            displayName = "Tiny",
            sourceLabel = "tiny.gguf",
            input = ggufBytes().inputStream(),
            sizeHint = 64,
            totalMemBytes = 8_000_000_000L
        )
        val defaults = MemoryDefaults()
        val coordinator = coordinator(
            store = store,
            scheduler = FakeScheduler(),
            storageBytes = 10_000,
            totalMemBytes = 8_000_000_000L,
            defaults = defaults
        )
        coordinator.setDefault(imported.id)
        assertEquals(imported.id, coordinator.defaultModelId())
        coordinator.deleteInstalled(imported.id)
        assertNull(coordinator.defaultModelId())
        assertTrue(store.list().isEmpty())
        root.deleteRecursively()
        Unit
    }

    @Test
    fun `adopted download keeps the catalog id and published checksum`() = runBlocking {
        val root = tempRoot()
        val store = GgufModelStore(root)
        val source = File(root, "incoming.gguf")
        source.writeBytes(ggufBytes())
        val adopted = store.adoptFile(
            displayName = "Qwen2.5 1.5B Instruct",
            sourceLabel = "https://huggingface.co/example/model.gguf",
            source = source,
            totalMemBytes = 8_000_000_000L,
            sha256 = "ABC",
            licenseName = "Apache-2.0",
            catalogId = "qwen2.5-1.5b-instruct-q4km"
        )
        assertEquals("abc", adopted.sha256)
        assertEquals("Apache-2.0", adopted.licenseName)
        assertEquals("qwen2.5-1.5b-instruct-q4km", adopted.catalogId)
        assertTrue(store.fileFor(adopted).isFile)
        assertFalse(source.exists())
        assertEquals(adopted.id, store.findByCatalogOrHash(adopted.catalogId, adopted.sha256)?.id)
        root.deleteRecursively()
        Unit
    }

    @Test
    fun `preference store remembers the default model id`() = runBlocking {
        val root = tempRoot()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(root, "gguf.preferences_pb") }
        )
        val store = GgufPreferenceStore(dataStore)
        assertNull(store.getDefaultModelId())
        store.setDefaultModelId("model-1")
        assertEquals("model-1", store.getDefaultModelId())
        store.setDefaultModelId(null)
        assertNull(store.getDefaultModelId())
        root.deleteRecursively()
        Unit
    }

    private fun coordinator(
        store: GgufModelStore,
        scheduler: FakeScheduler,
        storageBytes: Long,
        totalMemBytes: Long,
        metadata: FakeMetadata = FakeMetadata(GgufPublishedMetadata(sizeBytes = null, sha256 = null)),
        defaults: GgufDefaults = MemoryDefaults()
    ) = GgufModelCoordinator(
        store = store,
        defaults = defaults,
        memory = object : DeviceMemory {
            override fun totalBytes(): Long = totalMemBytes
            override fun availableBytes(): Long = totalMemBytes
        },
        storage = AvailableStorage { storageBytes },
        scheduler = scheduler,
        metadata = metadata
    )

    private fun tempRoot(): File = File(System.getProperty("java.io.tmpdir"), "gguf-manager-${System.nanoTime()}").apply { mkdirs() }

    private fun ggufBytes(): ByteArray {
        val body = ByteArrayOutputStream()
        fun writeU32(value: Long) {
            body.write((value and 0xff).toInt())
            body.write((value shr 8 and 0xff).toInt())
            body.write((value shr 16 and 0xff).toInt())
            body.write((value shr 24 and 0xff).toInt())
        }
        fun writeU64(value: Long) {
            var remaining = value
            repeat(8) {
                body.write((remaining and 0xff).toInt())
                remaining = remaining ushr 8
            }
        }
        writeU32(0x46554747L)
        writeU32(3L)
        writeU64(0L)
        writeU64(1L)
        val key = "llama.context_length".toByteArray(Charsets.UTF_8)
        writeU64(key.size.toLong())
        body.write(key)
        writeU32(4L)
        writeU32(4096L)
        return body.toByteArray()
    }

    private data class Verified(
        val fileName: String,
        val sizeBytes: Long,
        val sha256: String,
        val licenseName: String
    )

    private class MemoryDefaults : GgufDefaults {
        var id: String? = null
        override suspend fun getDefaultModelId(): String? = id
        override suspend fun setDefaultModelId(id: String?) {
            this.id = id
        }
    }

    private class FakeMetadata(
        var value: GgufPublishedMetadata
    ) : GgufRemoteMetadataSource {
        override suspend fun lookup(url: String): GgufPublishedMetadata = value
    }

    private class FakeScheduler : GgufDownloadScheduler {
        val enqueued = mutableListOf<GgufDownloadRequest>()
        val active = mutableSetOf<String>()
        private val snapshots = MutableStateFlow<List<GgufDownloadSnapshot>>(emptyList())

        override fun enqueue(request: GgufDownloadRequest) {
            enqueued += request
            active += request.downloadKey
        }

        override fun cancel(downloadKey: String) {
            active.remove(downloadKey)
        }

        override fun isActive(downloadKey: String): Boolean = downloadKey in active

        override fun observe(): Flow<List<GgufDownloadSnapshot>> = snapshots
    }
}
