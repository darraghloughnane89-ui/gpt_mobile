package dev.chungjungsoo.gptmobile.data.project.archive

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException

internal object ProjectFolderStage {
    fun stage(
        context: Context,
        uri: Uri,
        onProgress: (ProjectTransferProgress) -> Unit,
        isActive: () -> Boolean
    ): StagedArchive {
        if (!isActive()) throw CancellationException("Import cancelled")
        val rootId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (_: Exception) {
            throw ProjectArchiveException(ProjectArchiveFailure.UNREADABLE)
        }
        val nodes = mutableListOf<Node>()
        val caps = ArchiveCaps()
        val budget = ImportBudget(caps)
        fun walk(documentId: String, prefix: String, depth: Int) {
            if (!isActive()) throw CancellationException("Import cancelled")
            if (depth > 8) return
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, documentId)
            val cursor = context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                ),
                null,
                null,
                null
            ) ?: throw ProjectArchiveException(ProjectArchiveFailure.UNREADABLE)
            cursor.use { rows ->
                val idIndex = rows.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = rows.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = rows.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = rows.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (rows.moveToNext()) {
                    if (!isActive()) throw CancellationException("Import cancelled")
                    budget.entry()
                    val childId = if (idIndex >= 0) rows.getString(idIndex) else null
                    if (childId.isNullOrBlank()) continue
                    val name = if (nameIndex >= 0) rows.getString(nameIndex)?.trim().orEmpty() else ""
                    if (name.isEmpty() || name == ".DS_Store") continue
                    val mime = if (mimeIndex >= 0) rows.getString(mimeIndex) else null
                    val size = if (sizeIndex >= 0 && !rows.isNull(sizeIndex)) rows.getLong(sizeIndex) else -1L
                    val path = ArchivePaths.normalize(if (prefix.isEmpty()) name else "$prefix/$name") ?: continue
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(childId, path, depth + 1)
                    } else {
                        if (size > 0) budget.charge(size)
                        nodes += Node(
                            path = path,
                            documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, childId),
                            mimeType = mime,
                            size = size
                        )
                        onProgress(ProjectTransferProgress(ProjectTransferStage.READING, nodes.size, nodes.size, name))
                    }
                }
            }
        }
        walk(rootId, "", 0)
        val prefix = ArchivePaths.topFolderPrefix(nodes.map { it.path })
        val catalog = mutableListOf<ArchiveEntry>()
        val files = mutableListOf<StagedFile>()
        val skipped = mutableListOf<ImportSkip>()
        nodes.forEach { node ->
            val path = node.path.removePrefix(prefix)
            if (path.substringAfterLast('/').equals("import-map.json", ignoreCase = true)) return@forEach
            val opener = {
                context.contentResolver.openInputStream(node.documentUri)
                    ?: throw ProjectArchiveException(ProjectArchiveFailure.UNREADABLE)
            }
            when {
                ArchivePaths.isCatalog(path) -> {
                    val bytes = opener().use { stream ->
                        ProjectArchiveZip.readCapped(stream, caps.maxCatalogBytes)
                    }
                    if (bytes == null) {
                        if (path.substringAfterLast('/').lowercase() in setOf("conversations.json", "projects.json", "manifest.json")) {
                            throw ProjectArchiveException(ProjectArchiveFailure.TOO_LARGE)
                        }
                        skipped += ImportSkip(path, ImportSkipReason.TOO_LARGE)
                    } else if (bytes.isEmpty()) {
                        skipped += ImportSkip(path, ImportSkipReason.EMPTY)
                    } else {
                        catalog += ArchiveEntry(path, bytes)
                    }
                }
                node.size > caps.maxEntryBytes && !ArchivePaths.isZip(path) -> skipped += ImportSkip(path, ImportSkipReason.TOO_LARGE)
                node.size == 0L -> skipped += ImportSkip(path, ImportSkipReason.EMPTY)
                else -> files += StagedFile(path, node.size.coerceAtLeast(0L), node.mimeType, opener)
            }
        }
        return StagedArchive(catalog, files, skipped)
    }

    private data class Node(val path: String, val documentUri: Uri, val mimeType: String?, val size: Long)
}
