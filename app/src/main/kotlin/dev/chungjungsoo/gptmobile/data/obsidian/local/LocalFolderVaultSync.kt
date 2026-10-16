package dev.chungjungsoo.gptmobile.data.obsidian.local

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import dev.chungjungsoo.gptmobile.data.obsidian.redactVaultOutbound
import dev.chungjungsoo.gptmobile.data.obsidian.VaultCommit
import dev.chungjungsoo.gptmobile.data.obsidian.VaultCommitResult
import dev.chungjungsoo.gptmobile.data.obsidian.VaultPaths
import dev.chungjungsoo.gptmobile.data.obsidian.VaultReadResult
import dev.chungjungsoo.gptmobile.data.obsidian.VaultRemoteFile
import dev.chungjungsoo.gptmobile.data.obsidian.VaultSync
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secondary backend for a vault folder already on the phone. The folder can be
 * filled by a free sync the user already runs. This client does not talk to
 * Obsidian Sync or any other paid service.
 */
class LocalFolderVaultSync(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    private val rootFolder: String
) : VaultSync {

    override suspend fun readTree(): VaultReadResult = withContext(Dispatchers.IO) {
        val root = runCatching { treeDocumentUri() }.getOrElse {
            return@withContext VaultReadResult.Failed("Choose a vault folder before syncing.")
        }
        val files = mutableListOf<VaultRemoteFile>()
        val walked = try {
            walk(root, "")
        } catch (_: Exception) {
            return@withContext VaultReadResult.Failed("Could not read the vault folder.")
        }
        walked.forEach { (path, uri) ->
            if (!path.endsWith(".md") || !VaultPaths.isUnderRoot(path, rootFolder)) return@forEach
            val text = readText(uri) ?: return@withContext VaultReadResult.Failed("Could not read $path.")
            files += VaultRemoteFile(path = path, content = text, transportSha = sha256(text))
        }
        VaultReadResult.Ok(files)
    }

    override suspend fun commit(change: VaultCommit): VaultCommitResult = withContext(Dispatchers.IO) {
        if (change.files.isEmpty()) return@withContext VaultCommitResult.Ok("unchanged")
        val root = runCatching { treeDocumentUri() }.getOrElse {
            return@withContext VaultCommitResult.Failed("Choose a vault folder before syncing.")
        }
        try {
            change.files.forEach { file ->
                if (VaultPaths.confine(file.path, rootFolder) == null) {
                    error("Refusing to write outside the vault.")
                }
                writeText(root, file.path, redactVaultOutbound(file.content))
            }
            VaultCommitResult.Ok(sha256(change.files.joinToString("\n") { it.path + it.content }))
        } catch (error: Exception) {
            VaultCommitResult.Failed(error.message ?: "Could not write the vault folder.")
        }
    }

    private fun treeDocumentUri(): Uri {
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId)
    }

    private fun walk(directory: Uri, prefix: String): List<Pair<String, Uri>> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(directory)
        )
        val found = mutableListOf<Pair<String, Uri>>()
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val childId = cursor.getString(idColumn) ?: continue
                val mime = cursor.getString(mimeColumn)
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    found += walk(childUri, path)
                } else {
                    found += path to childUri
                }
            }
        }
        return found
    }

    private fun writeText(root: Uri, path: String, content: String) {
        val parent = ensureDirectory(root, path.substringBeforeLast("/", "").split("/").filter { it.isNotBlank() })
        val name = path.substringAfterLast("/")
        val existing = findChild(parent, name)
        val target = existing ?: DocumentsContract.createDocument(resolver, parent, "text/markdown", name)
            ?: error("Could not create $path.")
        resolver.openOutputStream(target, "wt")?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("Could not open $path.")
    }

    private fun ensureDirectory(root: Uri, segments: List<String>): Uri {
        var current = root
        segments.forEach { segment ->
            val existing = findChild(current, segment)
            current = if (existing != null) {
                existing
            } else {
                DocumentsContract.createDocument(
                    resolver,
                    current,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    segment
                ) ?: error("Could not create folder $segment.")
            }
        }
        return current
    }

    private fun findChild(directory: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getDocumentId(directory)
        )
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == name) {
                    val id = cursor.getString(idColumn) ?: return null
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                }
            }
        }
        return null
    }

    private fun readText(uri: Uri): String? = resolver.openInputStream(uri)?.use { stream ->
        stream.readBytes().toString(Charsets.UTF_8)
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

fun localFolderLabel(uri: String?): String? = uri?.substringAfterLast("/").takeIf { !it.isNullOrBlank() }
