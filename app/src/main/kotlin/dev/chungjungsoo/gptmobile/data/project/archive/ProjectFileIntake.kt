package dev.chungjungsoo.gptmobile.data.project.archive

import dev.chungjungsoo.gptmobile.data.project.PdfTextExtractor
import dev.chungjungsoo.gptmobile.data.project.ProjectContextLimits
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream

internal data class PreparedFile(
    val relativePath: String,
    val displayName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val extractedText: String?,
    val image: Boolean
)

internal data class IntakeResult(
    val files: List<PreparedFile>,
    val skipped: List<ImportSkip>
)

internal object ProjectFileIntake {
    private val textExtensions = setOf(
        "txt",
        "md",
        "markdown",
        "text",
        "json",
        "log",
        "html",
        "htm",
        "xhtml",
        "js",
        "mjs",
        "cjs"
    )

    fun prepare(
        file: ImportedFile,
        options: ProjectImportOptions = ProjectImportOptions(),
        images: ProjectImageProcessor = ProjectImageProcessor.Identity,
        caps: ArchiveCaps = ArchiveCaps()
    ): IntakeResult {
        val prepared = mutableListOf<PreparedFile>()
        val skipped = mutableListOf<ImportSkip>()
        accept(file, options, images, depth = 0, caps, prepared, skipped)
        return IntakeResult(prepared, skipped)
    }

    private fun accept(
        file: ImportedFile,
        options: ProjectImportOptions,
        images: ProjectImageProcessor,
        depth: Int,
        caps: ArchiveCaps,
        prepared: MutableList<PreparedFile>,
        skipped: MutableList<ImportSkip>
    ) {
        val path = file.relativePath.ifBlank { file.displayName }
        if (file.bytes.isEmpty()) {
            skipped += ImportSkip(path, ImportSkipReason.EMPTY)
            return
        }
        val extension = ArchivePaths.extension(path)
        val mime = file.mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val zipFile = ArchivePaths.isZip(path) || mime == "application/zip"
        val limit = if (zipFile) caps.maxCatalogBytes else caps.maxEntryBytes
        if (file.bytes.size > limit) {
            skipped += ImportSkip(path, ImportSkipReason.TOO_LARGE)
            return
        }
        try {
            when {
                extension == "docx" || mime.contains("wordprocessingml") -> convertDocx(file, path, options, images, caps, prepared, skipped)
                extension == "xlsx" || mime.contains("spreadsheetml") -> convertXlsx(file, path, caps, prepared, skipped)
                extension == "csv" || mime == "text/csv" -> convertCsv(file, path, caps, prepared, skipped)
                ArchivePaths.isZip(path) || mime == "application/zip" -> expandZip(file, path, depth, caps, options, images, prepared, skipped)
                extension in textExtensions || mime.startsWith("text/") || mime == "application/json" || mime == "application/javascript" ->
                    storeText(file, path, prepared)
                extension == "pdf" || mime == "application/pdf" -> storePdf(file, path, prepared)
                isImage(extension, mime) -> storeImage(file, path, options, images, caps, prepared, skipped)
                else -> skipped += ImportSkip(path, ImportSkipReason.UNSUPPORTED)
            }
        } catch (archive: ProjectArchiveException) {
            throw archive
        } catch (_: Exception) {
            skipped += ImportSkip(path, ImportSkipReason.UNREADABLE)
        }
    }

    private fun convertDocx(
        file: ImportedFile,
        path: String,
        options: ProjectImportOptions,
        images: ProjectImageProcessor,
        caps: ArchiveCaps,
        prepared: MutableList<PreparedFile>,
        skipped: MutableList<ImportSkip>
    ) {
        val document = DocxMarkdown.convert(file.bytes)
        if (document == null) {
            skipped += ImportSkip(path, ImportSkipReason.UNREADABLE)
            return
        }
        val markdownPath = replaceExtension(path, "md")
        if (document.markdown.isNotBlank()) {
            val bytes = document.markdown.toByteArray(Charsets.UTF_8)
            prepared += textFile(markdownPath, ArchivePaths.fileName(markdownPath), "text/markdown", bytes)
        }
        document.images.forEach { image ->
            accept(
                file = ImportedFile(
                    relativePath = "${directoryFor(path)}/${image.fileName}",
                    displayName = image.fileName,
                    bytes = image.bytes
                ),
                options = options,
                images = images,
                depth = 0,
                caps = caps,
                prepared = prepared,
                skipped = skipped
            )
        }
        if (document.markdown.isBlank() && document.images.isEmpty()) {
            skipped += ImportSkip(path, ImportSkipReason.EMPTY)
        }
    }

    private fun convertXlsx(
        file: ImportedFile,
        path: String,
        caps: ArchiveCaps,
        prepared: MutableList<PreparedFile>,
        skipped: MutableList<ImportSkip>
    ) {
        val markdown = XlsxMarkdown.convert(file.bytes, caps.tables)
        if (markdown.isNullOrBlank()) {
            skipped += ImportSkip(path, ImportSkipReason.EMPTY)
            return
        }
        val markdownPath = replaceExtension(path, "md")
        prepared += textFile(markdownPath, ArchivePaths.fileName(markdownPath), "text/markdown", markdown.toByteArray(Charsets.UTF_8))
    }

    private fun convertCsv(
        file: ImportedFile,
        path: String,
        caps: ArchiveCaps,
        prepared: MutableList<PreparedFile>,
        skipped: MutableList<ImportSkip>
    ) {
        val markdown = MarkdownTables.fromCsv(file.bytes.toString(Charsets.UTF_8), caps.tables)
        if (markdown.isBlank()) {
            skipped += ImportSkip(path, ImportSkipReason.EMPTY)
            return
        }
        prepared += textFile(path, ArchivePaths.fileName(path), "text/markdown", markdown.toByteArray(Charsets.UTF_8))
    }

    private fun expandZip(
        file: ImportedFile,
        path: String,
        depth: Int,
        caps: ArchiveCaps,
        options: ProjectImportOptions,
        images: ProjectImageProcessor,
        prepared: MutableList<PreparedFile>,
        skipped: MutableList<ImportSkip>
    ) {
        if (depth + 1 > caps.maxZipDepth) {
            skipped += ImportSkip(path, ImportSkipReason.TOO_DEEP)
            return
        }
        val leaves = mutableListOf<ImportedFile>()
        ZipTree.expand(
            bytes = file.bytes,
            prefix = ZipTree.prefix(path),
            depth = depth + 1,
            caps = caps,
            onFile = { leaves += it },
            onSkip = { skipped += it }
        )
        if (leaves.isEmpty() && skipped.none { it.path.startsWith(ZipTree.prefix(path)) }) {
            skipped += ImportSkip(path, ImportSkipReason.EMPTY)
        }
        leaves.forEach { leaf ->
            accept(leaf, options, images, depth + 1, caps, prepared, skipped)
        }
    }

    private fun storeText(file: ImportedFile, path: String, prepared: MutableList<PreparedFile>) {
        val mime = when (ArchivePaths.extension(path)) {
            "md", "markdown" -> "text/markdown"
            "json" -> "application/json"
            "js", "mjs", "cjs" -> "text/javascript"
            "html", "htm", "xhtml" -> "text/html"
            else -> file.mimeType?.substringBefore(';')?.trim()?.ifBlank { null } ?: "text/plain"
        }
        prepared += textFile(path, ArchivePaths.fileName(file.displayName.ifBlank { path }), mime, file.bytes)
    }

    private fun storePdf(file: ImportedFile, path: String, prepared: MutableList<PreparedFile>) {
        val text = PdfTextExtractor.extract(file.bytes, ProjectContextLimits.MAX_FILE_CHARS).ifBlank { null }
        prepared += PreparedFile(
            relativePath = path,
            displayName = ArchivePaths.fileName(file.displayName.ifBlank { path }),
            mimeType = "application/pdf",
            bytes = file.bytes,
            extractedText = text,
            image = false
        )
    }

    private fun storeImage(
        file: ImportedFile,
        path: String,
        options: ProjectImportOptions,
        images: ProjectImageProcessor,
        caps: ArchiveCaps,
        prepared: MutableList<PreparedFile>,
        skipped: MutableList<ImportSkip>
    ) {
        val mime = imageMime(path, file.mimeType)
        val stripped = if (options.stripGps) ExifStripper.strip(file.bytes) else file.bytes
        val processed = images.process(stripped, mime, options.keepOriginalImages)
        if (processed.bytes.isEmpty()) {
            skipped += ImportSkip(path, ImportSkipReason.EMPTY)
            return
        }
        if (processed.bytes.size > caps.maxEntryBytes) {
            skipped += ImportSkip(path, ImportSkipReason.TOO_LARGE)
            return
        }
        prepared += PreparedFile(
            relativePath = path,
            displayName = ArchivePaths.fileName(file.displayName.ifBlank { path }),
            mimeType = processed.mimeType.ifBlank { mime },
            bytes = processed.bytes,
            extractedText = null,
            image = true
        )
    }

    private fun textFile(path: String, displayName: String, mimeType: String, bytes: ByteArray): PreparedFile {
        val extracted = bytes.toString(Charsets.UTF_8).trim().take(ProjectContextLimits.MAX_FILE_CHARS).ifBlank { null }
        return PreparedFile(
            relativePath = path,
            displayName = ArchivePaths.fileName(displayName),
            mimeType = mimeType,
            bytes = bytes,
            extractedText = extracted,
            image = false
        )
    }

    private fun isImage(extension: String, mime: String): Boolean {
        if (mime.startsWith("image/")) return true
        return extension in setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")
    }

    private fun imageMime(path: String, fallback: String?): String = when (ArchivePaths.extension(path)) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> fallback?.substringBefore(';')?.trim()?.ifBlank { null } ?: "image/*"
    }

    private fun replaceExtension(path: String, extension: String): String {
        val slash = path.lastIndexOf('/')
        val name = path.substring(slash + 1)
        val stem = name.substringBeforeLast('.', name)
        val dir = if (slash >= 0) path.substring(0, slash + 1) else ""
        return "$dir$stem.$extension"
    }

    private fun directoryFor(path: String): String {
        val slash = path.lastIndexOf('/')
        val dir = if (slash >= 0) path.substring(0, slash + 1) else ""
        val stem = path.substring(slash + 1).substringBeforeLast('.', path.substring(slash + 1))
        return "$dir$stem"
    }
}

internal object ZipTree {
    fun prefix(path: String): String {
        val stripped = if (path.lowercase().endsWith(".zip")) path.dropLast(4) else path
        return stripped.ifBlank { "archive" }
    }

    fun expand(
        bytes: ByteArray,
        prefix: String,
        depth: Int,
        caps: ArchiveCaps,
        onFile: (ImportedFile) -> Unit,
        onSkip: (ImportSkip) -> Unit
    ) {
        val budget = ImportBudget(caps)
        expandStream(ByteArrayInputStream(bytes), prefix, depth, caps, budget, onFile, onSkip)
    }

    fun expandStream(
        input: java.io.InputStream,
        prefix: String,
        depth: Int,
        caps: ArchiveCaps,
        budget: ImportBudget,
        onFile: (ImportedFile) -> Unit,
        onSkip: (ImportSkip) -> Unit
    ) {
        if (depth > caps.maxZipDepth) {
            onSkip(ImportSkip(prefix, ImportSkipReason.TOO_DEEP))
            return
        }
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = ArchivePaths.normalize(entry.name)
                if (entry.isDirectory || name == null) {
                    if (name == null && !entry.isDirectory) ArchiveIO.drain(zip, budget)
                    zip.closeEntry()
                    continue
                }
                budget.entry()
                val path = "$prefix/$name"
                if (ArchivePaths.isZip(path)) {
                    expandNested(zip, path, depth, caps, budget, onFile, onSkip)
                } else {
                    val payload = ArchiveIO.readBudgeted(zip, caps.maxEntryBytes, budget)
                    when {
                        payload == null -> onSkip(ImportSkip(path, ImportSkipReason.TOO_LARGE))
                        payload.isEmpty() -> onSkip(ImportSkip(path, ImportSkipReason.EMPTY))
                        else -> onFile(ImportedFile(path, ArchivePaths.fileName(path), payload))
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun expandNested(
        zip: ZipInputStream,
        path: String,
        depth: Int,
        caps: ArchiveCaps,
        budget: ImportBudget,
        onFile: (ImportedFile) -> Unit,
        onSkip: (ImportSkip) -> Unit
    ) {
        if (depth + 1 > caps.maxZipDepth) {
            ArchiveIO.drain(zip, budget)
            onSkip(ImportSkip(path, ImportSkipReason.TOO_DEEP))
            return
        }
        val temp = Files.createTempFile("robin-nested", ".zip")
        val before = budget.total
        try {
            val spilled = ArchiveIO.spill(zip, temp, caps.maxCatalogBytes, budget)
            val charged = budget.total - before
            if (spilled.overflow || spilled.file == null) {
                onSkip(ImportSkip(path, if (spilled.size == 0L) ImportSkipReason.EMPTY else ImportSkipReason.TOO_LARGE))
            } else {
                budget.refund(charged)
                Files.newInputStream(temp).buffered().use { nested ->
                    expandStream(nested, prefix(path), depth + 1, caps, budget, onFile, onSkip)
                }
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
