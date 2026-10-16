package dev.chungjungsoo.gptmobile.data.project.archive

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException

object ProjectArchiveSession {
    internal suspend fun import(
        open: () -> InputStream,
        sink: ProjectArchiveSink,
        options: ProjectImportOptions = ProjectImportOptions(),
        imageProcessor: ProjectImageProcessor = ProjectImageProcessor.Identity,
        onProgress: (ProjectTransferProgress) -> Unit = {},
        isActive: () -> Boolean = { true },
        caps: ArchiveCaps = ArchiveCaps()
    ): ProjectArchiveResult {
        if (!isActive()) throw CancellationException("Import cancelled")
        val staged = ProjectArchiveStaging.stage(open, onProgress, isActive, caps)
        try {
            return importStaged(staged, sink, options, imageProcessor, onProgress, isActive, caps)
        } finally {
            staged.close()
        }
    }

    internal suspend fun importStaged(
        staged: StagedArchive,
        sink: ProjectArchiveSink,
        options: ProjectImportOptions = ProjectImportOptions(),
        imageProcessor: ProjectImageProcessor = ProjectImageProcessor.Identity,
        onProgress: (ProjectTransferProgress) -> Unit = {},
        isActive: () -> Boolean = { true },
        caps: ArchiveCaps = ArchiveCaps()
    ): ProjectArchiveResult {
        if (!isActive()) throw CancellationException("Import cancelled")
        onProgress(ProjectTransferProgress(ProjectTransferStage.PARSING, 0, staged.catalog.size))
        val projects = ProjectArchiveParser.parse(
            staged.catalog,
            ArchiveHints(
                hasProjectFiles = staged.files.isNotEmpty(),
                filePaths = staged.files.map { it.path }
            )
        )
        val imported = ProjectArchiveImporter.import(
            projects = projects,
            sink = sink,
            onProgress = onProgress,
            options = options,
            imageProcessor = imageProcessor,
            isActive = isActive
        )
        val target = imported.projects.singleOrNull()
            ?: imported.projects.firstOrNull { it.source == ImportSource.ROBIN }
            ?: imported.projects.firstOrNull()
            ?: return imported.copy(skipped = imported.skipped + staged.skipped)
        val map = sink.readMap(target.id)
        val fileMap = map.files.toMutableMap()
        var fileCount = 0
        var imageCount = 0
        val skipped = imported.skipped.toMutableList()
        skipped += staged.skipped
        val budget = ImportBudget(caps)
        val leafDirectory = Files.createTempDirectory("robin-leaves")
        try {
            staged.files.forEachIndexed { index, stagedFile ->
                if (!isActive()) throw CancellationException("Import cancelled")
                onProgress(
                    ProjectTransferProgress(
                        ProjectTransferStage.WRITING,
                        index,
                        staged.files.size,
                        ArchivePaths.fileName(stagedFile.path)
                    )
                )
                if (ArchivePaths.isZip(stagedFile.path)) {
                    val leaves = mutableListOf<Pair<String, Path>>()
                    try {
                        stagedFile.open().use { stream ->
                            ZipTree.expandStream(
                                input = stream,
                                prefix = ZipTree.prefix(stagedFile.path),
                                depth = 1,
                                caps = caps,
                                budget = budget,
                                onFile = { leaf ->
                                    val destination = leafDirectory.resolve("leaf-${leaves.size}")
                                    Files.write(destination, leaf.bytes)
                                    leaves += leaf.relativePath to destination
                                },
                                onSkip = { skipped += it }
                            )
                        }
                    } catch (archive: ProjectArchiveException) {
                        throw archive
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        skipped += ImportSkip(stagedFile.path, ImportSkipReason.UNREADABLE)
                    }
                    leaves.forEach { (path, file) ->
                        val bytes = Files.readAllBytes(file)
                        Files.deleteIfExists(file)
                        val ingested = ingestOne(
                            targetId = target.id,
                            path = path,
                            bytes = bytes,
                            mimeType = null,
                            fileMap = fileMap,
                            sink = sink,
                            options = options,
                            imageProcessor = imageProcessor,
                            isActive = isActive
                        )
                        fileCount += ingested.fileCount
                        imageCount += ingested.imageCount
                        skipped += ingested.skipped
                    }
                    return@forEachIndexed
                }
                val bytes = stagedFile.open().use { stream ->
                    ArchiveIO.readBudgeted(stream, caps.maxEntryBytes, budget)
                }
                if (bytes == null) {
                    skipped += ImportSkip(stagedFile.path, ImportSkipReason.TOO_LARGE)
                    return@forEachIndexed
                }
                val ingested = ingestOne(
                    targetId = target.id,
                    path = stagedFile.path,
                    bytes = bytes,
                    mimeType = stagedFile.mimeType,
                    fileMap = fileMap,
                    sink = sink,
                    options = options,
                    imageProcessor = imageProcessor,
                    isActive = isActive
                )
                fileCount += ingested.fileCount
                imageCount += ingested.imageCount
                skipped += ingested.skipped
            }
        } finally {
            leafDirectory.toFile().deleteRecursively()
        }
        sink.writeMap(target.id, map.copy(files = fileMap))
        onProgress(ProjectTransferProgress(ProjectTransferStage.WRITING, staged.files.size, staged.files.size, target.name))
        return imported.copy(
            fileCount = imported.fileCount + fileCount,
            imageCount = imported.imageCount + imageCount,
            skipped = skipped
        )
    }

    private suspend fun ingestOne(
        targetId: Int,
        path: String,
        bytes: ByteArray,
        mimeType: String?,
        fileMap: MutableMap<String, Int>,
        sink: ProjectArchiveSink,
        options: ProjectImportOptions,
        imageProcessor: ProjectImageProcessor,
        isActive: () -> Boolean
    ): FileIngestResult = ProjectArchiveImporter.ingestFiles(
        projectId = targetId,
        files = listOf(
            ImportedFile(
                relativePath = path,
                displayName = ArchivePaths.fileName(path),
                bytes = bytes,
                mimeType = mimeType
            )
        ),
        fileMap = fileMap,
        sink = sink,
        options = options,
        imageProcessor = imageProcessor,
        isActive = isActive
    )
}
