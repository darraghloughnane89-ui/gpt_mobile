package dev.chungjungsoo.gptmobile.data.project

import dev.chungjungsoo.gptmobile.data.project.archive.ArchiveCaps
import dev.chungjungsoo.gptmobile.data.project.archive.ArchiveEntry
import dev.chungjungsoo.gptmobile.data.project.archive.ExifStripper
import dev.chungjungsoo.gptmobile.data.project.archive.GrokMarkdownChat
import dev.chungjungsoo.gptmobile.data.project.archive.ImageScale
import dev.chungjungsoo.gptmobile.data.project.archive.ImportSkipReason
import dev.chungjungsoo.gptmobile.data.project.archive.ImportSource
import dev.chungjungsoo.gptmobile.data.project.archive.ImportedFile
import dev.chungjungsoo.gptmobile.data.project.archive.ImportedRole
import dev.chungjungsoo.gptmobile.data.project.archive.MarkdownTables
import dev.chungjungsoo.gptmobile.data.project.archive.ProcessedImage
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveException
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveFailure
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveParser
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveSession
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveStaging
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveZip
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectFileIntake
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectImageProcessor
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectImportOptions
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectTransferStage
import dev.chungjungsoo.gptmobile.data.project.archive.TableCaps
import dev.chungjungsoo.gptmobile.data.project.archive.ZipTree
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectImportFormatsTest {
    @Test
    fun limits_allowOneGibTotalAndKeepPerFileCapsSmaller() {
        assertTrue(ProjectArchiveZip.MAX_TOTAL_BYTES >= 1024L * 1024L * 1024L)
        assertEquals(32 * 1024 * 1024, ProjectArchiveZip.MAX_ENTRY_BYTES)
        assertTrue(ProjectArchiveZip.MAX_ENTRY_BYTES < ProjectArchiveZip.MAX_TOTAL_BYTES)
        assertEquals(2048, ProjectArchiveZip.MAX_IMAGE_EDGE)
        assertTrue(ProjectArchiveZip.MAX_CATALOG_BYTES > ProjectArchiveZip.MAX_ENTRY_BYTES)
    }

    @Test
    fun filesFolder_isNotStrippedWhenItIsTheOnlyRoot() {
        val parsed = ProjectArchiveParser.parse(
            listOf(ArchiveEntry("files/note.md", "alpha".toByteArray()))
        ).single()

        assertEquals("files/note.md", parsed.files.single().relativePath)
    }

    @Test
    fun grokMarkdown_readsHeaderSectionsStripsStatusAndKeepsEmptyTurns() {
        val parsed = ProjectArchiveParser.parse(
            listOf(ArchiveEntry("planning.md", grokMarkdown().toByteArray()))
        ).single()

        assertEquals(ImportSource.GROK, parsed.source)
        assertEquals("grok:grok-77", parsed.importKey)
        assertEquals("Planning", parsed.chats.single().title)
        assertEquals("grok-77", parsed.chats.single().externalId)
        assertEquals(Instant.parse("2024-01-02T03:04:05Z").epochSecond, parsed.chats.single().createdAt)
        assertEquals(
            listOf(ImportedRole.USER, ImportedRole.ASSISTANT, ImportedRole.USER, ImportedRole.ASSISTANT),
            parsed.chats.single().messages.map { it.role }
        )
        assertEquals(
            listOf("Hello", "Hi there", GrokMarkdownChat.EMPTY_PLACEHOLDER, GrokMarkdownChat.EMPTY_PLACEHOLDER),
            parsed.chats.single().messages.map { it.text }
        )
        assertFalse(parsed.chats.single().messages.any { it.text.contains("https://") })
        assertFalse(parsed.chats.single().messages.any { it.text.contains("Worked for") })
        assertFalse(parsed.chats.single().messages.any { it.text.contains("Thought for") })
    }

    @Test
    fun docx_extractsHeadingsTablesAndEmbeddedImages() {
        val intake = ProjectFileIntake.prepare(ImportedFile("files/report.docx", "report.docx", docxBytes()))

        val markdown = intake.files.first { it.relativePath == "files/report.md" }
        val text = markdown.bytes.toString(Charsets.UTF_8)
        assertTrue(text.contains("# Report"))
        assertTrue(text.contains("Hello world"))
        assertTrue(text.contains("![pixel.png](pixel.png)"))
        assertTrue(text.contains("| A | B |"))
        assertTrue(text.contains("| 1 | 2 |"))
        val image = intake.files.first { it.relativePath == "files/report/pixel.png" }
        assertTrue(image.image)
        assertTrue(image.bytes.contentEquals(byteArrayOf(9, 8, 7)))
        assertTrue(intake.skipped.isEmpty())
    }

    @Test
    fun xlsxAndCsv_becomeMarkdownTablesWithACap() {
        val sheet = ProjectFileIntake.prepare(ImportedFile("files/scores.xlsx", "scores.xlsx", xlsxBytes())).files.single()
        val text = sheet.bytes.toString(Charsets.UTF_8)
        assertTrue(text.contains("## Scores"))
        assertTrue(text.contains("| Hello | World |"))
        assertTrue(text.contains("| 3 | 4 |"))

        val csv = """
            Name,Note
            "A, B",ok
            ${List(5) { "row$it,value" }.joinToString("\n")}
        """.trimIndent()
        val capped = MarkdownTables.fromCsv(csv, TableCaps(maxRows = 2, maxCols = 2, maxCellChars = 20))
        assertTrue(capped.contains("| A, B | ok |"))
        assertTrue(capped.contains("truncated"))
        assertFalse(capped.contains("row4"))
    }

    @Test
    fun htmlJsonAndJs_areStoredAsText_andUnknownTypesAreSkipped() {
        val html = ProjectFileIntake.prepare(ImportedFile("files/page.html", "page.html", "<p>Hi</p>".toByteArray()))
        assertEquals("text/html", html.files.single().mimeType)
        assertEquals("<p>Hi</p>", html.files.single().extractedText)

        val json = ProjectFileIntake.prepare(ImportedFile("files/data.json", "data.json", "{\"a\":1}".toByteArray()))
        assertEquals("application/json", json.files.single().mimeType)
        assertEquals("{\"a\":1}", json.files.single().extractedText)

        val script = ProjectFileIntake.prepare(ImportedFile("files/app.js", "app.js", "const a = 1".toByteArray()))
        assertEquals("text/javascript", script.files.single().mimeType)

        val skipped = ProjectFileIntake.prepare(ImportedFile("files/clip.mp4", "clip.mp4", byteArrayOf(1, 2, 3)))
        assertTrue(skipped.files.isEmpty())
        assertEquals(ImportSkipReason.UNSUPPORTED, skipped.skipped.single().reason)
    }

    @Test
    fun nestedZip_expandsIntoSubfoldersAndListsUnsupportedLeaves() {
        val sink = MemoryArchiveSink()
        val stages = mutableListOf<ProjectTransferStage>()
        val zip = zipBytes(
            mapOf(
                "instructions.md" to "Be brief".toByteArray(),
                "files/bundle.zip" to zipBytes(
                    mapOf(
                        "readme.md" to "hello".toByteArray(),
                        "inner.zip" to zipBytes(
                            mapOf(
                                "pic.txt" to "nested-text".toByteArray(),
                                "clip.mp4" to byteArrayOf(1, 2, 3)
                            )
                        )
                    )
                )
            )
        )

        val result = runBlocking {
            ProjectArchiveSession.import(
                open = { ByteArrayInputStream(zip) },
                sink = sink,
                onProgress = { stages += it.stage }
            )
        }

        assertEquals("Be brief", sink.projects.single().instructions)
        assertEquals("hello", textFile(sink, "readme.md"))
        assertEquals("nested-text", textFile(sink, "pic.txt"))
        assertTrue(sink.files.none { it.displayName.endsWith(".zip") })
        assertTrue(sink.files.none { it.displayName == "clip.mp4" })
        assertEquals(listOf("files/bundle/inner/clip.mp4"), result.skipped.map { it.path })
        assertEquals(ImportSkipReason.UNSUPPORTED, result.skipped.single().reason)
        assertTrue(stages.contains(ProjectTransferStage.READING))
        assertTrue(stages.contains(ProjectTransferStage.WRITING))
        assertEquals(0, result.chatCount)
    }

    @Test
    fun nestedZip_countsExpandedLeavesOnceAgainstTheTotalCap() {
        val note = "hello world".toByteArray()
        val inner = zipBytes(mapOf("note.txt" to note))
        val outer = zipBytes(mapOf("pack.zip" to inner))
        val files = mutableListOf<ImportedFile>()
        val skipped = mutableListOf<ImportSkipReason>()
        ZipTree.expand(
            bytes = outer,
            prefix = "files",
            depth = 1,
            caps = ArchiveCaps(
                maxEntryBytes = 1000,
                maxCatalogBytes = 1000,
                maxTotalBytes = inner.size.toLong()
            ),
            onFile = { files += it },
            onSkip = { skipped += it.reason }
        )
        assertTrue(skipped.isEmpty())
        assertEquals("hello world", files.single().bytes.toString(Charsets.UTF_8))
        assertEquals("files/pack/note.txt", files.single().relativePath)
    }

    @Test
    fun stage_keepsFilePayloadsOutOfTheCatalog() {
        val zip = zipBytes(
            mapOf(
                "instructions.md" to "Be brief".toByteArray(),
                "files/note.txt" to "payload".toByteArray()
            )
        )
        val staged = ProjectArchiveStaging.stage(open = { ByteArrayInputStream(zip) })
        try {
            assertEquals(listOf("instructions.md"), staged.catalog.map { it.path })
            assertFalse(staged.catalog.any { it.bytes.contentEquals("payload".toByteArray()) })
            val file = staged.files.single()
            assertEquals("files/note.txt", file.path)
            assertTrue(file.open().use { it.readBytes() }.contentEquals("payload".toByteArray()))
        } finally {
            staged.close()
        }
    }

    @Test
    fun oversizedFile_isSkippedWhileTheRestImports_andTotalCapStillFails() {
        val zip = zipBytes(
            mapOf(
                "instructions.md" to "ok".toByteArray(),
                "files/big.bin" to ByteArray(20) { 7 },
                "files/small.txt" to "tiny".toByteArray()
            )
        )
        val sink = MemoryArchiveSink()
        val result = runBlocking {
            ProjectArchiveSession.import(
                open = { ByteArrayInputStream(zip) },
                sink = sink,
                caps = ArchiveCaps(maxEntryBytes = 8, maxCatalogBytes = 100, maxTotalBytes = 10_000)
            )
        }
        assertEquals("tiny", textFile(sink, "small.txt"))
        assertEquals(ImportSkipReason.TOO_LARGE, result.skipped.single { it.path == "files/big.bin" }.reason)

        val huge = zipBytes(mapOf("instructions.md" to ByteArray(30) { 'a'.code.toByte() }))
        val error = assertThrows(ProjectArchiveException::class.java) {
            ProjectArchiveStaging.stage(
                open = { ByteArrayInputStream(huge) },
                caps = ArchiveCaps(maxEntryBytes = 8, maxCatalogBytes = 100, maxTotalBytes = 16)
            )
        }
        assertEquals(ProjectArchiveFailure.TOO_LARGE, error.failure)
    }

    @Test
    fun images_stripGpsByDefault_andCanKeepTheOriginal() {
        val jpeg = gpsJpeg()
        assertTrue(ExifStripper.hasJpegGps(jpeg))
        var sawOriginal = true
        val processor = ProjectImageProcessor { bytes, mime, keepOriginal ->
            sawOriginal = keepOriginal
            assertFalse(ExifStripper.hasJpegGps(bytes))
            ProcessedImage(byteArrayOf(4, 5), mime, downscaled = !keepOriginal)
        }
        val scaled = ProjectFileIntake.prepare(
            ImportedFile("files/shot.jpg", "shot.jpg", jpeg, "image/jpeg"),
            ProjectImportOptions(),
            processor
        )
        assertFalse(sawOriginal)
        assertTrue(scaled.files.single().bytes.contentEquals(byteArrayOf(4, 5)))
        assertTrue(scaled.files.single().image)
        assertFalse(ExifStripper.hasJpegGps(scaled.files.single().bytes))

        val kept = ProjectFileIntake.prepare(
            ImportedFile("files/shot.jpg", "shot.jpg", jpeg, "image/jpeg"),
            ProjectImportOptions(keepOriginalImages = true, stripGps = false),
            ProjectImageProcessor.Identity
        )
        assertTrue(ExifStripper.hasJpegGps(kept.files.single().bytes))
        assertNull(kept.files.single().extractedText)
    }

    @Test
    fun imageScale_shrinksTheLongEdgeToTheCap() {
        assertEquals(1, ImageScale.sampleSize(2048, 100, 2048))
        assertEquals(2, ImageScale.sampleSize(5000, 3000, 2048))
        val (width, height) = ImageScale.targetSize(5000, 3000, 2048)
        assertTrue(maxOf(width, height) <= 2048)
        assertTrue(width >= 2000)
        assertTrue(height >= 1100)
    }

    @Test
    fun cancel_stopsBeforeAProjectIsWritten() {
        val sink = MemoryArchiveSink()
        val zip = zipBytes(mapOf("instructions.md" to "Be brief".toByteArray()))
        assertThrows(CancellationException::class.java) {
            runBlocking {
                ProjectArchiveSession.import(
                    open = { ByteArrayInputStream(zip) },
                    sink = sink,
                    isActive = { false }
                )
            }
        }
        assertTrue(sink.projects.isEmpty())
    }

    private fun textFile(sink: MemoryArchiveSink, name: String): String =
        sink.files.first { it.displayName == name }.bytes.toString(Charsets.UTF_8)

    private fun grokMarkdown() = """
        # Planning

        **Date:** 2024-01-02T03:04:05Z
        **Chat ID:** grok-77
        **URL:** https://grok.example/chat/grok-77
        **Messages:** 4

        ---

        ## User

        Hello

        ---

        ## Grok

        Worked for 12s
        Thought for 3s

        Hi there

        ---

        ## User

        ---

        ## Grok

        Worked for 1m 3s
    """.trimIndent()

    private fun docxBytes(): ByteArray = zipBytes(
        mapOf(
            "word/document.xml" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                  <w:body>
                    <w:p>
                      <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                      <w:r><w:t>Report</w:t></w:r>
                    </w:p>
                    <w:p>
                      <w:r><w:t>Hello </w:t></w:r>
                      <w:r><w:t>world</w:t></w:r>
                      <w:r><w:drawing><a:blip r:embed="rId5"/></w:drawing></w:r>
                    </w:p>
                    <w:tbl>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc>
                      </w:tr>
                      <w:tr>
                        <w:tc><w:p><w:r><w:t>1</w:t></w:r></w:p></w:tc>
                        <w:tc><w:p><w:r><w:t>2</w:t></w:r></w:p></w:tc>
                      </w:tr>
                    </w:tbl>
                  </w:body>
                </w:document>
            """.trimIndent().toByteArray(),
            "word/_rels/document.xml.rels" to """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/pixel.png"/>
                </Relationships>
            """.trimIndent().toByteArray(),
            "word/media/pixel.png" to byteArrayOf(9, 8, 7)
        )
    )

    private fun xlsxBytes(): ByteArray = zipBytes(
        mapOf(
            "xl/sharedStrings.xml" to """
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <si><t>Hello</t></si>
                  <si><t>World</t></si>
                </sst>
            """.trimIndent().toByteArray(),
            "xl/workbook.xml" to """
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets>
                    <sheet name="Scores" sheetId="1" r:id="rId1"/>
                  </sheets>
                </workbook>
            """.trimIndent().toByteArray(),
            "xl/_rels/workbook.xml.rels" to """
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
            """.trimIndent().toByteArray(),
            "xl/worksheets/sheet1.xml" to """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                    </row>
                    <row r="2">
                      <c r="A2"><v>3</v></c>
                      <c r="B2"><v>4</v></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent().toByteArray()
        )
    )

    private fun gpsJpeg(): ByteArray {
        val tiff = ByteArrayOutputStream()
        fun write16(value: Int) {
            tiff.write(value and 0xFF)
            tiff.write((value shr 8) and 0xFF)
        }
        fun write32(value: Int) {
            write16(value and 0xFFFF)
            write16(value shr 16)
        }
        tiff.write('I'.code)
        tiff.write('I'.code)
        write16(42)
        write32(8)
        write16(1)
        write16(0x8825)
        write16(4)
        write32(1)
        write32(26)
        write32(0)
        val payload = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0) + tiff.toByteArray()
        val length = payload.size + 2
        val output = ByteArrayOutputStream()
        output.write(0xFF)
        output.write(0xD8)
        output.write(0xFF)
        output.write(0xE1)
        output.write((length shr 8) and 0xFF)
        output.write(length and 0xFF)
        output.write(payload)
        output.write(0xFF)
        output.write(0xD9)
        return output.toByteArray()
    }

    private fun zipBytes(entries: Map<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
