package dev.chungjungsoo.gptmobile.data.obsidian.github

import dev.chungjungsoo.gptmobile.data.obsidian.VaultCommit
import dev.chungjungsoo.gptmobile.data.obsidian.VaultCommitResult
import dev.chungjungsoo.gptmobile.data.obsidian.VaultFileWrite
import dev.chungjungsoo.gptmobile.data.obsidian.VaultMarkdown
import dev.chungjungsoo.gptmobile.data.obsidian.VaultNoteType
import dev.chungjungsoo.gptmobile.data.obsidian.VaultReadResult
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubVaultSyncTest {
    private val server = MockWebServer()
    private val http = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    private val calls = mutableListOf<RecordedRequest>()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun readTree_filtersRootAndDecodesMarkdown() = runBlocking {
        val markdown = VaultMarkdown.render(
            dev.chungjungsoo.gptmobile.data.obsidian.VaultDocument(
                id = "mem-tea",
                type = VaultNoteType.MEMORY,
                updatedEpochSeconds = 20,
                title = "Tea",
                body = "Likes tea"
            )
        )
        dispatch { request ->
            when {
                request.path.orEmpty().contains("/git/trees/") -> json(
                    """{"truncated":false,"tree":[
                        {"path":"Robin/Memory/Tea.md","type":"blob","sha":"abc"},
                        {"path":"Notes/other.md","type":"blob","sha":"def"}
                    ]}"""
                )
                request.path.orEmpty().contains("/contents/") -> json(
                    """{"encoding":"base64","sha":"abc","content":"${Base64.getEncoder().encodeToString(markdown.toByteArray())}"}"""
                )
                else -> MockResponse().setResponseCode(404)
            }
        }

        val result = sync().readTree()

        val files = (result as VaultReadResult.Ok).files
        assertEquals(listOf("Robin/Memory/Tea.md"), files.map { it.path })
        assertEquals("mem-tea", VaultMarkdown.parse(files.single().content)?.id)
        assertTrue(calls.all { it.getHeader("Authorization") == "Bearer test-token" })
    }

    @Test
    fun commit_batchesFilesAndUpdatesRefWithoutForce() = runBlocking {
        dispatch { request ->
            when {
                request.method == "GET" && request.path.orEmpty().contains("/git/ref/") ->
                    json("""{"object":{"sha":"headsha","type":"commit"}}""")
                request.method == "GET" && request.path.orEmpty().contains("/git/commits/") ->
                    json("""{"sha":"headsha","tree":{"sha":"basesha"}}""")
                request.method == "POST" && request.path.orEmpty().endsWith("/git/blobs") ->
                    json("""{"sha":"blob-${calls.count { it.path.orEmpty().endsWith("/git/blobs") }}"}""")
                request.method == "POST" && request.path.orEmpty().endsWith("/git/trees") ->
                    json("""{"sha":"treesha"}""")
                request.method == "POST" && request.path.orEmpty().endsWith("/git/commits") ->
                    json("""{"sha":"newcommit"}""")
                request.method == "PATCH" && request.path.orEmpty().contains("/git/refs/heads/") ->
                    json("""{"ref":"refs/heads/main","object":{"sha":"newcommit"}}""")
                else -> MockResponse().setResponseCode(404).setBody(request.path.orEmpty())
            }
        }

        val result = sync().commit(
            VaultCommit(
                message = "Sync Robin vault",
                files = listOf(
                    VaultFileWrite("Robin/Memory/Tea.md", "one"),
                    VaultFileWrite("Robin/Skills/summarise/SKILL.md", "two")
                )
            )
        )

        assertEquals(VaultCommitResult.Ok("newcommit"), result)
        val patch = calls.single { it.method == "PATCH" }
        val patchBody = patch.body.readUtf8()
        assertTrue(patch.path.orEmpty().contains("/git/refs/heads/main"))
        assertTrue(patchBody.contains("\"force\":false"))
        assertTrue(calls.none { it.body.peek().readUtf8().contains("\"force\":true") })
        assertEquals(2, calls.count { it.method == "POST" && it.path.orEmpty().endsWith("/git/blobs") })
        val treeBody = calls.single { it.path.orEmpty().endsWith("/git/trees") }.body.readUtf8()
        assertTrue(treeBody.contains("Robin/Memory/Tea.md"))
        assertTrue(treeBody.contains("Robin/Skills/summarise/SKILL.md"))
        assertTrue(treeBody.contains("basesha"))
    }

    @Test
    fun commit_refusesPathsThatEscapeTheVault() = runBlocking {
        dispatch { MockResponse().setResponseCode(500).setBody("""{"message":"should not be called"}""") }

        val result = sync().commit(
            VaultCommit(
                "Sync Robin vault",
                listOf(VaultFileWrite("Robin/../.github/workflows/pwn.yml", "evil"))
            )
        )

        assertTrue(result is VaultCommitResult.Failed)
        assertEquals(0, calls.size)
    }

    @Test
    fun commit_redactsSecretsInBlobContent() = runBlocking {
        dispatch { request ->
            when {
                request.method == "GET" && request.path.orEmpty().contains("/git/ref/") ->
                    json("""{"object":{"sha":"headsha"}}""")
                request.method == "GET" && request.path.orEmpty().contains("/git/commits/") ->
                    json("""{"sha":"headsha","tree":{"sha":"basesha"}}""")
                request.method == "POST" && request.path.orEmpty().endsWith("/git/blobs") ->
                    json("""{"sha":"blobsha"}""")
                request.method == "POST" && request.path.orEmpty().endsWith("/git/trees") ->
                    json("""{"sha":"treesha"}""")
                request.method == "POST" && request.path.orEmpty().endsWith("/git/commits") ->
                    json("""{"sha":"newcommit"}""")
                request.method == "PATCH" -> json("""{"object":{"sha":"newcommit"}}""")
                else -> MockResponse().setResponseCode(500)
            }
        }
        val secret = "sk-live-secret-value"
        val result = sync().commit(
            VaultCommit("Sync Robin vault", listOf(VaultFileWrite("Robin/Memory/Tea.md", "key $secret")))
        )

        assertTrue(result is VaultCommitResult.Ok)
        val blob = calls.single { it.path.orEmpty().endsWith("/git/blobs") }.body.readUtf8()
        assertFalse(blob.contains(secret))
    }

    @Test
    fun commit_rejectedFastForward_doesNotForcePush() = runBlocking {
        dispatch { request ->
            when {
                request.method == "GET" && request.path.orEmpty().contains("/git/ref/") ->
                    json("""{"object":{"sha":"headsha"}}""")
                request.method == "GET" && request.path.orEmpty().contains("/git/commits/") ->
                    json("""{"sha":"headsha","tree":{"sha":"basesha"}}""")
                request.method == "POST" && request.path.orEmpty().endsWith("/git/blobs") ->
                    json("""{"sha":"blobsha"}""")
                request.method == "POST" && request.path.orEmpty().endsWith("/git/trees") ->
                    json("""{"sha":"treesha"}""")
                request.method == "POST" && request.path.orEmpty().endsWith("/git/commits") ->
                    json("""{"sha":"newcommit"}""")
                request.method == "PATCH" ->
                    MockResponse().setResponseCode(422).setBody("""{"message":"Update is not a fast forward"}""")
                else -> MockResponse().setResponseCode(404)
            }
        }

        val result = sync().commit(
            VaultCommit("Sync Robin vault", listOf(VaultFileWrite("Robin/Memory/Tea.md", "one")))
        )

        assertTrue(result is VaultCommitResult.Rejected)
        assertEquals(1, calls.count { it.method == "PATCH" })
        assertTrue(calls.single { it.method == "PATCH" }.body.readUtf8().contains("\"force\":false"))
    }

    private fun sync() = GitHubVaultSync(
        http = http,
        apiBase = server.url("/").toString().trimEnd('/'),
        token = "test-token",
        owner = "darraghloughnane89-ui",
        repo = "knowledge-vault",
        branch = "main",
        rootFolder = "Robin"
    )

    private fun dispatch(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                calls += request
                return handler(request)
            }
        }
    }

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
