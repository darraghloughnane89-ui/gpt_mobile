package dev.chungjungsoo.gptmobile.data.hermes

import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesEndpointTest {
    @Test
    fun parseHermesEndpoint_lanHost_addsHttpAndStripsV1() {
        val endpoint = parseHermesEndpoint("http://192.168.1.20:8642/v1/")
        assertEquals("http://192.168.1.20:8642", endpoint?.base)
        assertNull(endpoint?.profileHint)
    }

    @Test
    fun parseHermesEndpoint_bareTailscaleAddress_keepsHttp() {
        val endpoint = parseHermesEndpoint("100.64.1.8:8642")
        assertEquals("http://100.64.1.8:8642", endpoint?.base)
        assertEquals("http://100.64.1.8:8642/v1", endpoint?.let { directApiRoot(it.base) })
    }

    @Test
    fun parseHermesEndpoint_profilePrefix_becomesHint() {
        val endpoint = parseHermesEndpoint("https://mac.tailnet.ts.net:8642/p/cbre-admin/v1")
        assertEquals("https://mac.tailnet.ts.net:8642", endpoint?.base)
        assertEquals("cbre-admin", endpoint?.profileHint)
        assertEquals(
            "https://mac.tailnet.ts.net:8642/p/cbre-admin/v1",
            multiplexApiRoot(endpoint!!.base, "cbre-admin")
        )
    }

    @Test
    fun parseHermesEndpoint_rejectsNonHttp() {
        assertNull(parseHermesEndpoint("ftp://example.com"))
        assertNull(parseHermesEndpoint(""))
    }

    @Test
    fun defaultProfile_isCbreAdmin() {
        assertEquals("cbre-admin", HermesDefaults.PROFILE)
        assertTrue(isValidHermesProfile(HermesDefaults.PROFILE))
    }
}

class HermesSseParserTest {
    @Test
    fun consume_chatDeltasKeepaliveAndToolProgress_emitsTextThenDone() {
        val parser = HermesSseParser()
        val events = mutableListOf<HermesStreamEvent>()
        val stream = """
            data: {"choices":[{"delta":{"content":"Hello"}}]}

            : keepalive

            event: hermes.tool.progress
            data: {"tool":"terminal","preview":"ls"}

            data: {"choices":[{"delta":{"content":" Hermes"},"finish_reason":null}]}

            data: [DONE]

        """.trimIndent()
        stream.lineSequence().forEach { line -> events += parser.consume(line) }
        events += parser.finish()

        assertEquals(HermesStreamEvent.Text("Hello"), events[0])
        assertEquals(HermesStreamEvent.Tool("terminal: ls"), events[1])
        assertEquals(HermesStreamEvent.Text(" Hermes"), events[2])
        assertTrue(events.last() is HermesStreamEvent.Completed)
    }

    @Test
    fun consume_runEvents_readsDeltaAndCompletedOutput() {
        val parser = HermesSseParser()
        val events = mutableListOf<HermesStreamEvent>()
        listOf(
            "event: message.delta",
            """data: {"text":"Working"}""",
            "",
            "event: run.completed",
            """data: {"output":"Working done"}""",
            ""
        ).forEach { line -> events += parser.consume(line) }
        assertEquals(HermesStreamEvent.Text("Working"), events[0])
        assertEquals(HermesStreamEvent.Completed("Working done"), events[1])
    }

    @Test
    fun parseJsonBody_nonStreamingCompletion_returnsText() {
        val events = HermesSseParser.parseJsonBody(
            """{"choices":[{"message":{"role":"assistant","content":"Done"},"finish_reason":"stop"}]}"""
        )
        assertEquals(HermesStreamEvent.Text("Done"), events.first())
        assertTrue(events.last() is HermesStreamEvent.Completed)
    }
}

class HermesContentTest {
    @Test
    fun summary_isShorterThanFullTranscript_andKeepsLastUserTurn() {
        val turns = listOf(
            HermesTranscriptTurn("You", "First question about the lease"),
            HermesTranscriptTurn("Assistant", "MIDDLE-ONLY ".repeat(80)),
            HermesTranscriptTurn("You", "A middle question that should be dropped"),
            HermesTranscriptTurn("Assistant", "A long answer ".repeat(40)),
            HermesTranscriptTurn("You", "What is the notice period?")
        )
        val full = HermesTranscript.full("Lease", turns)
        val summary = HermesTranscript.summary("Lease", turns)
        assertTrue(full.contains("First question about the lease"))
        assertTrue(full.contains("A middle question that should be dropped"))
        assertTrue(full.contains("What is the notice period?"))
        assertFalse(summary.contains("A middle question that should be dropped"))
        assertTrue(summary.contains("What is the notice period?"))
        assertTrue(summary.contains("Summary of"))
        assertTrue(summary.length < full.length)
    }

    @Test
    fun buildHermesUserContent_image_inlinesDataUrl_andBinaryIsNoted() {
        val png = ByteArray(8) { it.toByte() }
        val content = buildHermesUserContent(
            kind = HermesHandoffKind.FILE,
            text = "Look at this",
            files = listOf(
                HermesPreparedFile("shot.png", "image/png", png, png.size.toLong(), false),
                HermesPreparedFile("notes.pdf", "application/pdf", ByteArray(4), 4, false)
            )
        )
        assertEquals(1, content.imageDataUrls.size)
        assertTrue(content.imageDataUrls.single().startsWith("data:image/png;base64,"))
        assertEquals(Base64.getEncoder().encodeToString(png), content.imageDataUrls.single().substringAfter(","))
        assertTrue(content.text.contains("notes.pdf"))
        assertTrue(content.text.contains("does not accept other uploads"))
        val body = chatCompletionBody("cbre-admin", emptyList(), content)
        assertTrue(body.contains("\"stream\":true"))
        assertTrue(body.contains("image_url"))
        assertTrue(body.contains("cbre-admin"))
    }

    @Test
    fun handoffBody_redactsSecretsAndMarksContentUntrusted() {
        val secret = "sk-live-secret-value"
        val content = buildHermesUserContent(
            kind = HermesHandoffKind.TRANSCRIPT,
            text = "Please summarise. The key is $secret",
            files = listOf(
                HermesPreparedFile(
                    "notes.txt",
                    "text/plain",
                    "token = $secret".toByteArray(),
                    16,
                    false
                )
            )
        )
        val body = chatCompletionBody("cbre-admin", emptyList(), content)

        assertFalse(body.contains(secret))
        assertTrue(body.contains("untrusted"))
        assertFalse(body.contains("Keep your tools"))
    }

    @Test
    fun load_refusesPathsOutsideTheInbox() {
        val root = File.createTempFile("inbox", "dir").apply {
            delete()
            mkdirs()
        }
        val outside = File.createTempFile("secret", ".txt")
        val inside = File(root, "notes.txt")
        try {
            outside.writeText("secret")
            inside.writeText("hello")
            val blocked = HermesFileLoader.load(
                HermesAttachment("secret.txt", outside.absolutePath, "text/plain"),
                listOf(root)
            )
            val allowed = HermesFileLoader.load(
                HermesAttachment("notes.txt", inside.absolutePath, "text/plain"),
                listOf(root)
            )
            assertTrue(blocked.missing)
            assertFalse(allowed.missing)
        } finally {
            outside.delete()
            inside.delete()
            root.deleteRecursively()
        }
    }
}

class HermesDeliveryTest {
    @Test
    fun reducer_enqueueAndRecover_keepsUnacceptedSendQueued() = runBlocking {
        val queued = HermesThreadReducer.enqueue(
            snapshot = HermesThreadSnapshot(),
            request = HermesHandoffRequest(HermesHandoffKind.TASK, "Ask Hermes to file this"),
            newId = sequentialIds(),
            now = 1L
        )
        val sending = HermesThreadReducer.markSending(queued, queued.threads.single().turns[1].id)
        val recovered = HermesThreadReducer.recover(sending)
        val assistant = recovered.threads.single().turns[1]
        assertEquals(HermesDeliveryStatus.QUEUED, assistant.status)
        assertEquals("3", assistant.idempotencyKey)
    }

    @Test
    fun reducer_retryAfterAccept_usesNewIdempotencyKey() {
        val snapshot = HermesThreadReducer.enqueue(
            HermesThreadSnapshot(),
            HermesHandoffRequest(HermesHandoffKind.MESSAGE, "Hello"),
            sequentialIds(),
            1L
        )
        val assistantId = snapshot.threads.single().turns[1].id
        val interrupted = HermesThreadReducer.applyEvent(
            HermesThreadReducer.markAccepted(snapshot, assistantId),
            assistantId,
            HermesStreamEvent.Failed("dropped")
        ).let { HermesThreadReducer.markInterrupted(it, assistantId) }
        val retried = HermesThreadReducer.retry(interrupted, assistantId, "new-key")
        assertEquals("new-key", retried.threads.single().turns[1].idempotencyKey)
        assertEquals(HermesDeliveryStatus.QUEUED, retried.threads.single().turns[1].status)
    }

    @Test
    fun drain_streamsReply_andKeepsIdempotencyKey() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore("?")
                return when {
                    path == "/health" || path == "/v1/health" -> json("""{"status":"ok"}""")
                    path.startsWith("/p/") -> MockResponse().setResponseCode(404)
                    path == "/v1/models" -> json("""{"data":[{"id":"cbre-admin"}]}""")
                    path == "/v1/capabilities" -> json("""{"chat_completions":true}""")
                    path == "/v1/chat/completions" -> MockResponse()
                        .setHeader("Content-Type", "text/event-stream")
                        .setBody(
                            """
                            data: {"choices":[{"delta":{"content":"Hello"}}]}

                            data: {"choices":[{"delta":{"content":" Hermes"}}]}

                            data: [DONE]

                            """.trimIndent()
                        )
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val directory = File.createTempFile("hermes", "dir").apply {
                delete()
                mkdirs()
            }
            val threads = FileHermesThreadStore(File(directory, "threads.json"))
            val settings = MemoryHermesSettingsStore(HermesSettings(endpoint = server.url("/").toString().trimEnd('/')))
            val credentials = MemoryHermesCredentialStore("secret-token")
            val processor = HermesDeliveryProcessor(threads, settings, credentials, OkHttpHermesTransport())
            threads.update {
                HermesThreadReducer.enqueue(
                    it,
                    HermesHandoffRequest(HermesHandoffKind.MESSAGE, "Ping"),
                    sequentialIds(),
                    5L
                )
            }
            val retry = processor.drain(0)
            assertFalse(retry)
            val assistant = threads.snapshot.value.threads.single().turns[1]
            assertEquals(HermesDeliveryStatus.DELIVERED, assistant.status)
            assertEquals("Hello Hermes", assistant.text)
            assertEquals(HermesRouteMode.DIRECT, settings.current().routeMode)
            val chat = generateSequence { server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS) }
                .first { it.path?.substringBefore("?") == "/v1/chat/completions" }
            assertEquals("Bearer secret-token", chat.getHeader("Authorization"))
            assertEquals("3", chat.getHeader("Idempotency-Key"))
            assertEquals(threads.snapshot.value.activeThreadId, chat.getHeader("X-Hermes-Session-Id"))
            assertEquals(
                "robin:${threads.snapshot.value.activeThreadId}",
                chat.getHeader("X-Hermes-Session-Key")
            )
            assertTrue(chat.body.readUtf8().contains("Ping"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun probe_profilePrefixRejected_doesNotUseDefaultProfile() = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/health") -> json("""{"status":"ok"}""")
                    path.startsWith("/p/cbre-admin/") -> MockResponse().setResponseCode(401)
                    path.startsWith("/v1/models") -> json("""{"data":[{"id":"hermes-agent"}]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val result = OkHttpHermesTransport().probe(
                endpoint = server.url("/").toString().trimEnd('/'),
                profile = "cbre-admin",
                token = "default-key"
            )
            assertTrue(result is HermesProbeResult.ProfileMismatch)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun drain_networkFailure_leavesMessageQueued() = runBlocking {
        val threads = FileHermesThreadStore(File.createTempFile("hermes-queue", ".json"))
        val settings = MemoryHermesSettingsStore(
            HermesSettings(endpoint = "http://127.0.0.1:9", profile = "cbre-admin", routeMode = HermesRouteMode.DIRECT, modelId = "cbre-admin")
        )
        val processor = HermesDeliveryProcessor(
            threads = threads,
            settingsStore = settings,
            credentials = MemoryHermesCredentialStore("secret"),
            transport = object : HermesTransport {
                override suspend fun probe(endpoint: String, profile: String, token: String) = HermesProbeResult.Unreachable("down")

                override suspend fun streamChat(
                    route: HermesResolvedRoute,
                    token: String,
                    sessionId: String,
                    sessionKey: String,
                    idempotencyKey: String,
                    body: String,
                    onAccepted: suspend () -> Unit,
                    onEvent: suspend (HermesStreamEvent) -> Unit
                ): Unit = throw HermesNetworkException(java.io.IOException("connection refused"))
            }
        )
        threads.update {
            HermesThreadReducer.enqueue(it, HermesHandoffRequest(HermesHandoffKind.TASK, "Retry me"), sequentialIds(), 1L)
        }
        assertTrue(processor.drain(0))
        assertEquals(HermesDeliveryStatus.QUEUED, threads.snapshot.value.threads.single().turns[1].status)
    }

    @Test
    fun handoff_withoutSettings_staysQueuedForLater() = runBlocking {
        val threads = FileHermesThreadStore(File.createTempFile("hermes-handoff", ".json"))
        val scheduler = MemoryHermesWorkScheduler()
        val handoff = HermesHandoff(
            threads = threads,
            settingsStore = MemoryHermesSettingsStore(),
            credentials = MemoryHermesCredentialStore(),
            scheduler = scheduler
        )
        val result = handoff.submit(HermesHandoffRequest(HermesHandoffKind.MESSAGE, "From the share sheet"))
        assertEquals(HermesHandoffResult.QUEUED_NEEDS_SETUP, result)
        assertEquals(1, scheduler.calls)
        assertEquals(HermesDeliveryStatus.QUEUED, threads.snapshot.value.threads.single().turns[1].status)
    }
}

private fun sequentialIds(): () -> String {
    var next = 0
    return { next++.toString() }
}

private fun json(body: String): MockResponse = MockResponse()
    .setHeader("Content-Type", "application/json")
    .setBody(body)
