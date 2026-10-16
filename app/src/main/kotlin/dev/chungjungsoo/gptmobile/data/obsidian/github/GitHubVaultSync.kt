package dev.chungjungsoo.gptmobile.data.obsidian.github

import dev.chungjungsoo.gptmobile.data.obsidian.redactVaultOutbound
import dev.chungjungsoo.gptmobile.data.obsidian.VaultCommit
import dev.chungjungsoo.gptmobile.data.obsidian.VaultCommitResult
import dev.chungjungsoo.gptmobile.data.obsidian.VaultFileWrite
import dev.chungjungsoo.gptmobile.data.obsidian.VaultPaths
import dev.chungjungsoo.gptmobile.data.obsidian.VaultReadResult
import dev.chungjungsoo.gptmobile.data.obsidian.VaultRemoteFile
import dev.chungjungsoo.gptmobile.data.obsidian.VaultSync
import dev.chungjungsoo.gptmobile.util.Brand
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * GitHub Contents and Git Data APIs. Commits are batched and the branch ref is
 * updated with force = false, so a rejected fast-forward never overwrites the remote.
 *
 * JGit (EDL, GPL-compatible) was considered and left out. A phone-side clone
 * still ends in the same non-fast-forward rejection, and git's line merge can
 * splice two chat transcripts. Content hashes, updated times, and the resolve
 * screen handle those conflicts without a local object database.
 */
class GitHubVaultSync(
    private val http: OkHttpClient,
    private val apiBase: String,
    private val token: String,
    private val owner: String,
    private val repo: String,
    private val branch: String,
    private val rootFolder: String
) : VaultSync {

    override suspend fun readTree(): VaultReadResult = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext VaultReadResult.Failed("Add a GitHub token before syncing.")
        val tree = get("/repos/${segment(owner)}/${segment(repo)}/git/trees/${segment(branch)}?recursive=1")
        if (tree.code == 409) {
            return@withContext VaultReadResult.Ok(emptyList())
        }
        if (tree.code !in 200..299) return@withContext VaultReadResult.Failed(tree.error("Could not list the vault."))
        val parsed = runCatching { json.decodeFromString<TreeResponse>(tree.body) }.getOrElse {
            return@withContext VaultReadResult.Failed("GitHub returned an unreadable tree.")
        }
        if (parsed.truncated) {
            return@withContext VaultReadResult.Failed("The repository tree is too large to sync in one pass.")
        }
        val files = mutableListOf<VaultRemoteFile>()
        parsed.tree.filter { it.type == "blob" && it.path.endsWith(".md") && VaultPaths.isUnderRoot(it.path, rootFolder) }
            .forEach { item ->
                val file = readFile(item.path) ?: return@withContext VaultReadResult.Failed("Could not read ${item.path}.")
                files += file
            }
        VaultReadResult.Ok(files)
    }

    override suspend fun commit(change: VaultCommit): VaultCommitResult = withContext(Dispatchers.IO) {
        if (change.files.any { VaultPaths.confine(it.path, rootFolder) == null }) {
            return@withContext VaultCommitResult.Failed("Refusing to write outside the vault.")
        }
        if (token.isBlank()) return@withContext VaultCommitResult.Failed("Add a GitHub token before syncing.")
        if (change.files.isEmpty()) return@withContext VaultCommitResult.Ok("unchanged")
        val refPath = "/repos/${segment(owner)}/${segment(repo)}/git/ref/heads/${segment(branch)}"
        val ref = get(refPath)
        if (ref.code == 409) return@withContext createInitialCommit(change)
        if (ref.code !in 200..299) return@withContext VaultCommitResult.Failed(ref.error("Could not read the branch."))
        val head = runCatching { json.decodeFromString<RefResponse>(ref.body).target.sha }.getOrElse {
            return@withContext VaultCommitResult.Failed("GitHub returned an unreadable branch ref.")
        }
        val commit = get("/repos/${segment(owner)}/${segment(repo)}/git/commits/$head")
        if (commit.code !in 200..299) return@withContext VaultCommitResult.Failed(commit.error("Could not read the latest commit."))
        val baseTree = runCatching { json.decodeFromString<CommitResponse>(commit.body).tree.sha }.getOrElse {
            return@withContext VaultCommitResult.Failed("GitHub returned an unreadable commit.")
        }
        val blobs = mutableListOf<Pair<String, String>>()
        change.files.forEach { file ->
            val blob = post(
                "/repos/${segment(owner)}/${segment(repo)}/git/blobs",
                buildJsonObject {
                    put("content", redactVaultOutbound(file.content))
                    put("encoding", "utf-8")
                }.toString()
            )
            if (blob.code !in 200..299) return@withContext VaultCommitResult.Failed(blob.error("Could not write ${file.path}."))
            val sha = runCatching { json.decodeFromString<ShaResponse>(blob.body).sha }.getOrNull()
                ?: return@withContext VaultCommitResult.Failed("GitHub returned an unreadable blob.")
            blobs += file.path to sha
        }
        val treeBody = buildTreeBody(baseTree, blobs)
        val tree = post("/repos/${segment(owner)}/${segment(repo)}/git/trees", treeBody)
        if (tree.code !in 200..299) return@withContext VaultCommitResult.Failed(tree.error("Could not build a git tree."))
        val treeSha = runCatching { json.decodeFromString<ShaResponse>(tree.body).sha }.getOrNull()
            ?: return@withContext VaultCommitResult.Failed("GitHub returned an unreadable tree.")
        val commitBody = buildJsonObject {
            put("message", change.message)
            put("tree", treeSha)
            putJsonArray("parents", listOf(head))
        }.toString()
        val created = post("/repos/${segment(owner)}/${segment(repo)}/git/commits", commitBody)
        if (created.code !in 200..299) return@withContext VaultCommitResult.Failed(created.error("Could not create a commit."))
        val commitSha = runCatching { json.decodeFromString<ShaResponse>(created.body).sha }.getOrNull()
            ?: return@withContext VaultCommitResult.Failed("GitHub returned an unreadable commit.")
        val updated = patch(
            "/repos/${segment(owner)}/${segment(repo)}/git/refs/heads/${segment(branch)}",
            buildJsonObject {
                put("sha", commitSha)
                put("force", false)
            }.toString()
        )
        if (updated.code == 422 || updated.code == 409) {
            return@withContext VaultCommitResult.Rejected(updated.error("The branch moved. Sync again. Nothing was force-pushed."))
        }
        if (updated.code !in 200..299) return@withContext VaultCommitResult.Failed(updated.error("Could not update the branch."))
        VaultCommitResult.Ok(commitSha)
    }

    private fun createInitialCommit(change: VaultCommit): VaultCommitResult {
        if (change.files.any { VaultPaths.confine(it.path, rootFolder) == null }) {
            return VaultCommitResult.Failed("Refusing to write outside the vault.")
        }
        val blobs = mutableListOf<Pair<String, String>>()
        change.files.forEach { file ->
            val blob = post(
                "/repos/${segment(owner)}/${segment(repo)}/git/blobs",
                buildJsonObject {
                    put("content", redactVaultOutbound(file.content))
                    put("encoding", "utf-8")
                }.toString()
            )
            if (blob.code !in 200..299) return VaultCommitResult.Failed(blob.error("Could not write ${file.path}."))
            val sha = runCatching { json.decodeFromString<ShaResponse>(blob.body).sha }.getOrNull()
                ?: return VaultCommitResult.Failed("GitHub returned an unreadable blob.")
            blobs += file.path to sha
        }
        val tree = post("/repos/${segment(owner)}/${segment(repo)}/git/trees", buildTreeBody(null, blobs))
        if (tree.code !in 200..299) return VaultCommitResult.Failed(tree.error("Could not build a git tree."))
        val treeSha = runCatching { json.decodeFromString<ShaResponse>(tree.body).sha }.getOrNull()
            ?: return VaultCommitResult.Failed("GitHub returned an unreadable tree.")
        val created = post(
            "/repos/${segment(owner)}/${segment(repo)}/git/commits",
            buildJsonObject {
                put("message", change.message)
                put("tree", treeSha)
                putJsonArray("parents", emptyList())
            }.toString()
        )
        if (created.code !in 200..299) return VaultCommitResult.Failed(created.error("Could not create the first commit."))
        val commitSha = runCatching { json.decodeFromString<ShaResponse>(created.body).sha }.getOrNull()
            ?: return VaultCommitResult.Failed("GitHub returned an unreadable commit.")
        val ref = post(
            "/repos/${segment(owner)}/${segment(repo)}/git/refs",
            buildJsonObject {
                put("ref", "refs/heads/$branch")
                put("sha", commitSha)
            }.toString()
        )
        if (ref.code !in 200..299) return VaultCommitResult.Failed(ref.error("Could not create the branch."))
        return VaultCommitResult.Ok(commitSha)
    }

    private fun readFile(path: String): VaultRemoteFile? {
        val encoded = path.split("/").joinToString("/") { segment(it) }
        val response = get("/repos/${segment(owner)}/${segment(repo)}/contents/$encoded?ref=${segment(branch)}")
        if (response.code !in 200..299) return null
        val parsed = runCatching { json.decodeFromString<ContentsResponse>(response.body) }.getOrNull() ?: return null
        val content = decodeContent(parsed) ?: return null
        return VaultRemoteFile(path = path, content = content, transportSha = parsed.sha)
    }

    private fun decodeContent(parsed: ContentsResponse): String? {
        val raw = parsed.content ?: return null
        if (parsed.encoding != null && parsed.encoding != "base64") return raw
        return runCatching { String(Base64.getMimeDecoder().decode(raw), Charsets.UTF_8) }.getOrNull()
    }

    private fun get(path: String): HttpResult = execute(Request.Builder().url(url(path)).get())

    private fun post(path: String, body: String): HttpResult = execute(
        Request.Builder().url(url(path)).post(body.toRequestBody(JSON))
    )

    private fun patch(path: String, body: String): HttpResult = execute(
        Request.Builder().url(url(path)).patch(body.toRequestBody(JSON))
    )

    private fun execute(builder: Request.Builder): HttpResult {
        val request = builder
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", Brand.NAME)
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                HttpResult(response.code, response.body.string())
            }
        } catch (error: Exception) {
            HttpResult(0, error.message ?: "Network error")
        }
    }

    private fun url(path: String): String = apiBase.trimEnd('/') + path

    private fun segment(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private fun buildTreeBody(baseTree: String?, blobs: List<Pair<String, String>>): String {
        val entries = blobs.joinToString(",") { (path, sha) ->
            """{"path":${quote(path)},"mode":"100644","type":"blob","sha":${quote(sha)}}"""
        }
        val base = if (baseTree == null) "" else """"base_tree":${quote(baseTree)},"""
        return "{$base\"tree\":[$entries]}"
    }

    private fun quote(value: String): String = buildJsonObject { put("v", value) }.toString().removePrefix("{\"v\":").removeSuffix("}")

    private fun HttpResult.error(fallback: String): String {
        val message = runCatching { json.decodeFromString<ErrorResponse>(body).message }.getOrNull()
        val raw = message?.takeIf { it.isNotBlank() } ?: fallback
        return if (token.isBlank()) raw else raw.replace(token, "•••")
    }

    private data class HttpResult(val code: Int, val body: String)

    @Serializable
    private data class TreeResponse(
        val truncated: Boolean = false,
        val tree: List<TreeItem> = emptyList()
    )

    @Serializable
    private data class TreeItem(
        val path: String,
        val type: String,
        val sha: String = ""
    )

    @Serializable
    private data class RefResponse(
        @SerialName("object") val target: RefObject
    )

    @Serializable
    private data class RefObject(val sha: String)

    @Serializable
    private data class CommitResponse(val sha: String = "", val tree: CommitTree)

    @Serializable
    private data class CommitTree(val sha: String)

    @Serializable
    private data class ShaResponse(val sha: String)

    @Serializable
    private data class ContentsResponse(
        val encoding: String? = null,
        val content: String? = null,
        val sha: String
    )

    @Serializable
    private data class ErrorResponse(val message: String? = null)

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
    }
}

private fun JsonObjectBuilder.putJsonArray(name: String, values: List<String>) {
    put(
        name,
        buildJsonArray {
            values.forEach { add(JsonPrimitive(it)) }
        }
    )
}

fun githubCommitMessage(): String = "Sync ${Brand.NAME} vault"

fun githubWrites(files: List<Pair<String, String>>): List<VaultFileWrite> =
    files.map { (path, content) -> VaultFileWrite(path, content) }
