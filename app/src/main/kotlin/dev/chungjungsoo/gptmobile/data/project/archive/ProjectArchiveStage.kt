package dev.chungjungsoo.gptmobile.data.project.archive

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException

internal data class ArchiveCaps(
    val maxEntryBytes: Int = ProjectArchiveZip.MAX_ENTRY_BYTES,
    val maxCatalogBytes: Int = ProjectArchiveZip.MAX_CATALOG_BYTES,
    val maxTotalBytes: Long = ProjectArchiveZip.MAX_TOTAL_BYTES,
    val maxEntries: Int = ProjectArchiveZip.MAX_ENTRIES,
    val maxZipDepth: Int = ProjectArchiveZip.MAX_ZIP_DEPTH,
    val tables: TableCaps = TableCaps()
)

internal class ImportBudget(private val caps: ArchiveCaps) {
    var total: Long = 0
        private set
    private var entries: Int = 0

    fun charge(bytes: Long) {
        if (bytes <= 0L) return
        total += bytes
        if (total > caps.maxTotalBytes) throw ProjectArchiveException(ProjectArchiveFailure.TOO_LARGE)
    }

    fun refund(bytes: Long) {
        total = (total - bytes).coerceAtLeast(0L)
    }

    fun entry() {
        entries += 1
        if (entries > caps.maxEntries) throw ProjectArchiveException(ProjectArchiveFailure.TOO_LARGE)
    }
}

internal object ArchiveIO {
    fun drain(input: InputStream, budget: ImportBudget) {
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            budget.charge(count.toLong())
        }
    }

    fun readBudgeted(input: InputStream, maxStore: Int, budget: ImportBudget): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var stored = 0
        var overflow = false
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            budget.charge(count.toLong())
            if (!overflow && stored <= maxStore - count) {
                output.write(buffer, 0, count)
                stored += count
            } else {
                overflow = true
            }
        }
        return if (overflow) null else output.toByteArray()
    }

    fun spill(input: InputStream, destination: Path, maxStore: Int, budget: ImportBudget): Spilled {
        var output: java.io.OutputStream? = Files.newOutputStream(destination)
        val buffer = ByteArray(8192)
        var stored = 0
        var read = 0L
        var overflow = false
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                read += count
                budget.charge(count.toLong())
                if (!overflow && count <= maxStore && stored <= maxStore - count) {
                    output?.write(buffer, 0, count)
                    stored += count
                } else if (!overflow) {
                    overflow = true
                    output?.close()
                    output = null
                    Files.deleteIfExists(destination)
                }
            }
        } catch (failure: ProjectArchiveException) {
            output?.close()
            output = null
            Files.deleteIfExists(destination)
            throw failure
        } finally {
            output?.close()
        }
        return Spilled(file = if (overflow) null else destination, size = read, overflow = overflow)
    }
}

internal data class Spilled(val file: Path?, val size: Long, val overflow: Boolean)

class StagedFile(
    val path: String,
    val size: Long,
    val mimeType: String?,
    private val opener: () -> InputStream
) {
    fun open(): InputStream = opener()
}

class StagedArchive(
    val catalog: List<ArchiveEntry>,
    val files: List<StagedFile>,
    val skipped: List<ImportSkip>,
    private val onClose: () -> Unit = {}
) : AutoCloseable {
    override fun close() {
        onClose()
    }
}

internal object ProjectArchiveStaging {
    fun stage(
        open: () -> InputStream,
        onProgress: (ProjectTransferProgress) -> Unit = {},
        isActive: () -> Boolean = { true },
        caps: ArchiveCaps = ArchiveCaps()
    ): StagedArchive {
        if (!isActive()) throw CancellationException("Import cancelled")
        val directory = Files.createTempDirectory("robin-import")
        try {
            val budget = ImportBudget(caps)
            val held = mutableListOf<Held>()
            val skipped = mutableListOf<ImportSkip>()
            var index = 0
            open().use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        if (!isActive()) throw CancellationException("Import cancelled")
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) {
                            zip.closeEntry()
                            continue
                        }
                        val path = ArchivePaths.normalize(entry.name)
                        if (path == null || isIgnored(path)) {
                            ArchiveIO.drain(zip, budget)
                            zip.closeEntry()
                            continue
                        }
                        budget.entry()
                        onProgress(ProjectTransferProgress(ProjectTransferStage.READING, held.size + skipped.size, 0, ArchivePaths.fileName(path)))
                        if (ArchivePaths.isZip(path)) {
                            expandZipEntry(zip, path, depth = 1, directory, budget, caps, held, skipped) { index++ }
                        } else {
                            val limit = if (mightBeCatalog(path)) caps.maxCatalogBytes else caps.maxEntryBytes
                            val destination = directory.resolve("part-${index++}")
                            val spilled = ArchiveIO.spill(zip, destination, limit, budget)
                            when {
                                spilled.overflow && isOversizedCatalog(path) ->
                                    throw ProjectArchiveException(ProjectArchiveFailure.TOO_LARGE)
                                spilled.overflow -> skipped += ImportSkip(path, ImportSkipReason.TOO_LARGE)
                                spilled.size == 0L -> {
                                    Files.deleteIfExists(destination)
                                    skipped += ImportSkip(path, ImportSkipReason.EMPTY)
                                }
                                else -> held += Held(path, destination, spilled.size)
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
            val prefix = ArchivePaths.topFolderPrefix(held.map { it.path } + skipped.map { it.path })
            val unwrapped = held.map { it.copy(path = it.path.removePrefix(prefix)) }
            val unwrappedSkips = skipped.map { it.copy(path = it.path.removePrefix(prefix)) }.toMutableList()
            val catalog = mutableListOf<ArchiveEntry>()
            val files = mutableListOf<StagedFile>()
            unwrapped.forEach { item ->
                when {
                    isIgnored(item.path) -> Files.deleteIfExists(item.file)
                    ArchivePaths.isCatalog(item.path) -> {
                        if (item.size > caps.maxCatalogBytes) {
                            unwrappedSkips += ImportSkip(item.path, ImportSkipReason.TOO_LARGE)
                            Files.deleteIfExists(item.file)
                        } else {
                            catalog += ArchiveEntry(item.path, Files.readAllBytes(item.file))
                            Files.deleteIfExists(item.file)
                        }
                    }
                    item.size > caps.maxEntryBytes -> {
                        unwrappedSkips += ImportSkip(item.path, ImportSkipReason.TOO_LARGE)
                        Files.deleteIfExists(item.file)
                    }
                    else -> files += StagedFile(item.path, item.size, null) { Files.newInputStream(item.file) }
                }
            }
            return StagedArchive(catalog, files, unwrappedSkips) {
                directory.toFile().deleteRecursively()
            }
        } catch (failure: Throwable) {
            directory.toFile().deleteRecursively()
            throw failure
        }
    }

    private fun expandZipEntry(
        zip: ZipInputStream,
        path: String,
        depth: Int,
        directory: Path,
        budget: ImportBudget,
        caps: ArchiveCaps,
        held: MutableList<Held>,
        skipped: MutableList<ImportSkip>,
        nextIndex: () -> Int
    ) {
        if (depth > caps.maxZipDepth) {
            ArchiveIO.drain(zip, budget)
            skipped += ImportSkip(path, ImportSkipReason.TOO_DEEP)
            return
        }
        val container = directory.resolve("zip-${nextIndex()}")
        val before = budget.total
        val spilled = ArchiveIO.spill(zip, container, caps.maxCatalogBytes, budget)
        if (spilled.overflow || spilled.file == null) {
            skipped += ImportSkip(path, if (spilled.size == 0L) ImportSkipReason.EMPTY else ImportSkipReason.TOO_LARGE)
            return
        }
        val containerSize = budget.total - before
        budget.refund(containerSize)
        try {
            ZipInputStream(Files.newInputStream(container).buffered()).use { nested ->
                while (true) {
                    val entry = nested.nextEntry ?: break
                    val name = ArchivePaths.normalize(entry.name)
                    if (entry.isDirectory || name == null) {
                        if (name == null && !entry.isDirectory) ArchiveIO.drain(nested, budget)
                        nested.closeEntry()
                        continue
                    }
                    budget.entry()
                    val child = "${ZipTree.prefix(path)}/$name"
                    if (ArchivePaths.isZip(child)) {
                        expandZipEntry(nested, child, depth + 1, directory, budget, caps, held, skipped, nextIndex)
                    } else {
                        val destination = directory.resolve("part-${nextIndex()}")
                        val leaf = ArchiveIO.spill(nested, destination, caps.maxEntryBytes, budget)
                        when {
                            leaf.overflow -> skipped += ImportSkip(child, ImportSkipReason.TOO_LARGE)
                            leaf.size == 0L -> {
                                Files.deleteIfExists(destination)
                                skipped += ImportSkip(child, ImportSkipReason.EMPTY)
                            }
                            else -> held += Held(child, destination, leaf.size)
                        }
                    }
                    nested.closeEntry()
                }
            }
        } catch (archive: ProjectArchiveException) {
            throw archive
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            budget.charge(containerSize)
            skipped += ImportSkip(path, ImportSkipReason.UNREADABLE)
        } finally {
            Files.deleteIfExists(container)
        }
    }

    private fun mightBeCatalog(path: String): Boolean {
        val extension = ArchivePaths.extension(path)
        return extension in setOf("json", "md", "markdown", "txt") || ArchivePaths.isStructural(path)
    }

    private fun isOversizedCatalog(path: String): Boolean {
        val name = path.substringAfterLast('/').lowercase()
        return name in setOf("conversations.json", "projects.json", "manifest.json")
    }

    private fun isIgnored(path: String): Boolean {
        val name = path.substringAfterLast('/').lowercase()
        return name == "import-map.json"
    }

    private data class Held(val path: String, val file: Path, val size: Long)
}
