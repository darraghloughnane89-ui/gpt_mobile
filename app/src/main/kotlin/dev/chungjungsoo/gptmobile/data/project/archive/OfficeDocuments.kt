package dev.chungjungsoo.gptmobile.data.project.archive

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

internal data class TableCaps(
    val maxSheets: Int = ProjectArchiveZip.MAX_TABLE_SHEETS,
    val maxRows: Int = ProjectArchiveZip.MAX_TABLE_ROWS,
    val maxCols: Int = ProjectArchiveZip.MAX_TABLE_COLS,
    val maxCellChars: Int = ProjectArchiveZip.MAX_CELL_CHARS
)

internal data class DocxImage(val fileName: String, val bytes: ByteArray)

internal data class DocxDocument(val markdown: String, val images: List<DocxImage>)

internal object MarkdownTables {
    fun fromCsv(text: String, caps: TableCaps = TableCaps()): String = render(parseCsv(text), caps)

    fun render(rows: List<List<String>>, caps: TableCaps = TableCaps()): String {
        val source = rows.filterNot { row -> row.all { it.isBlank() } }
        if (source.isEmpty()) return ""
        val width = source.maxOf { it.size }.coerceAtLeast(1)
        val usedWidth = width.coerceAtMost(caps.maxCols)
        val truncatedCols = width > caps.maxCols
        val bodyLimit = caps.maxRows
        val kept = if (source.size - 1 > bodyLimit) source.take(bodyLimit + 1) else source
        val truncatedRows = source.size - 1 > bodyLimit
        val lines = StringBuilder()
        lines.append(line(kept.first(), usedWidth, caps.maxCellChars))
        lines.append('\n')
        lines.append(List(usedWidth) { "---" }.joinToString(" | ", "| ", " |"))
        kept.drop(1).forEach { row ->
            lines.append('\n')
            lines.append(line(row, usedWidth, caps.maxCellChars))
        }
        if (truncatedRows || truncatedCols) {
            lines.append("\n\n")
            lines.append("Table truncated to ${caps.maxRows} rows and ${caps.maxCols} columns.")
        }
        return lines.toString()
    }

    private fun line(row: List<String>, width: Int, maxCellChars: Int): String {
        val cells = (0 until width).map { index -> escape(row.getOrElse(index) { "" }, maxCellChars) }
        return cells.joinToString(" | ", "| ", " |")
    }

    private fun escape(value: String, maxCellChars: Int): String {
        val cleaned = value
            .replace("\r\n", " ")
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace("|", "\\|")
            .trim()
        return if (cleaned.length <= maxCellChars) cleaned else cleaned.take(maxCellChars)
    }

    internal fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        val source = text.trimStart('\uFEFF')
        var index = 0
        while (index < source.length) {
            val char = source[index]
            if (quoted) {
                if (char == '"') {
                    if (index + 1 < source.length && source[index + 1] == '"') {
                        cell.append('"')
                        index += 2
                        continue
                    }
                    quoted = false
                } else {
                    cell.append(char)
                }
            } else {
                when (char) {
                    '"' -> quoted = true
                    ',' -> {
                        row += cell.toString()
                        cell.clear()
                    }
                    '\n' -> {
                        row += cell.toString()
                        rows += row
                        row = mutableListOf()
                        cell.clear()
                    }
                    '\r' -> Unit
                    else -> cell.append(char)
                }
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }
}

internal object DocxMarkdown {
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val imageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

    fun convert(bytes: ByteArray): DocxDocument? {
        val entries = ZipBytes.read(bytes) ?: return null
        val document = entries["word/document.xml"] ?: return null
        val names = relationshipTargets(entries["word/_rels/document.xml.rels"])
        val markdown = render(document, names.mapValues { fileName(it.value) })
        val images = linkedImages(entries, names)
        if (markdown.isBlank() && images.isEmpty()) return null
        return DocxDocument(markdown.trim(), images)
    }

    private fun linkedImages(entries: Map<String, ByteArray>, names: Map<String, String>): List<DocxImage> {
        val images = mutableListOf<DocxImage>()
        val seen = mutableSetOf<String>()
        fun add(target: String) {
            val path = resolveTarget(target)
            val payload = entries[path] ?: return
            val name = uniqueName(fileName(path), seen)
            if (ArchivePaths.extension(name) !in imageExtensions) return
            images += DocxImage(name, payload)
        }
        names.values.forEach(::add)
        entries.keys.filter { it.startsWith("word/media/") }.sorted().forEach { path ->
            if (images.none { it.fileName == fileName(path) }) add(path.removePrefix("word/"))
        }
        return images
    }

    private fun uniqueName(name: String, seen: MutableSet<String>): String {
        if (seen.add(name)) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var index = 2
        var candidate = "$stem-$index$ext"
        while (!seen.add(candidate)) {
            index++
            candidate = "$stem-$index$ext"
        }
        return candidate
    }

    private fun render(xml: ByteArray, embeds: Map<String, String>): String {
        val reader = xmlReader(xml)
        val out = StringBuilder()
        val paragraph = StringBuilder()
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var inTable = false
        var inCell = false
        var heading = 0
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                    "tbl" -> {
                        flushParagraph(out, paragraph, heading, inTable, inCell, cell)
                        inTable = true
                        rows.clear()
                    }
                    "tr" -> row = mutableListOf()
                    "tc" -> {
                        inCell = true
                        cell.clear()
                    }
                    "p" -> {
                        heading = 0
                        paragraph.clear()
                    }
                    "pStyle" -> heading = headingLevel(attribute(reader, "val"))
                    "outlineLvl" -> {
                        val level = attribute(reader, "val")?.toIntOrNull()
                        if (level != null) heading = (level + 1).coerceIn(1, 6)
                    }
                    "t" -> paragraph.append(elementText(reader))
                    "tab" -> paragraph.append('\t')
                    "br", "cr" -> paragraph.append('\n')
                    "blip" -> {
                        val id = attribute(reader, "embed") ?: reader.getAttributeValue(REL, "embed")
                        val name = id?.let { embeds[it] }
                        if (name != null) {
                            if (paragraph.isNotEmpty() && !paragraph.last().isWhitespace()) paragraph.append(' ')
                            paragraph.append("![").append(name).append("](").append(name).append(")")
                        }
                    }
                }
                XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
                    "p" -> flushParagraph(out, paragraph, heading, inTable, inCell, cell)
                    "tc" -> {
                        row += cell.toString().trim()
                        cell.clear()
                        inCell = false
                    }
                    "tr" -> if (row.isNotEmpty()) rows += row.toList()
                    "tbl" -> {
                        val table = MarkdownTables.render(rows)
                        if (table.isNotBlank()) appendBlock(out, table)
                        inTable = false
                        rows.clear()
                    }
                }
            }
        }
        reader.close()
        return out.toString().trim()
    }

    private fun flushParagraph(
        out: StringBuilder,
        paragraph: StringBuilder,
        heading: Int,
        inTable: Boolean,
        inCell: Boolean,
        cell: StringBuilder
    ) {
        val text = paragraph.toString().trim()
        paragraph.clear()
        if (inCell) {
            if (text.isEmpty()) return
            if (cell.isNotEmpty()) cell.append(' ')
            cell.append(text.replace('\n', ' '))
            return
        }
        if (inTable || text.isEmpty()) return
        val block = if (heading > 0) "${"#".repeat(heading)} $text" else text
        appendBlock(out, block)
    }

    private fun appendBlock(out: StringBuilder, block: String) {
        if (block.isBlank()) return
        if (out.isNotEmpty()) out.append("\n\n")
        out.append(block)
    }

    private fun headingLevel(style: String?): Int {
        if (style.isNullOrBlank()) return 0
        val normalized = style.lowercase().replace(" ", "")
        if (normalized == "title") return 1
        return Regex("heading([1-6])").find(normalized)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun relationshipTargets(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val reader = xmlReader(bytes)
        val targets = mutableMapOf<String, String>()
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.localName == "Relationship") {
                val id = attribute(reader, "Id") ?: continue
                val target = attribute(reader, "Target") ?: continue
                val type = attribute(reader, "Type").orEmpty()
                if (type.contains("image") || ArchivePaths.extension(target) in imageExtensions) {
                    targets[id] = target
                }
            }
        }
        reader.close()
        return targets
    }

    private fun resolveTarget(target: String): String {
        val cleaned = target.trim().removePrefix("/")
        return when {
            cleaned.startsWith("word/") -> cleaned
            cleaned.startsWith("media/") -> "word/$cleaned"
            else -> "word/${cleaned.removePrefix("../")}"
        }
    }

    private fun fileName(path: String): String = path.substringAfterLast('/').ifBlank { "image" }
}

internal object XlsxMarkdown {
    fun convert(bytes: ByteArray, caps: TableCaps = TableCaps()): String? {
        val entries = ZipBytes.read(bytes) ?: return null
        val shared = sharedStrings(entries["xl/sharedStrings.xml"])
        val sheets = sheetList(entries)
        if (sheets.isEmpty()) return null
        val out = StringBuilder()
        var truncatedSheets = false
        sheets.take(caps.maxSheets).forEach { sheet ->
            val xml = entries[sheet.path] ?: return@forEach
            val rows = sheetRows(xml, shared, caps)
            val table = MarkdownTables.render(rows, caps)
            if (table.isBlank()) return@forEach
            if (out.isNotEmpty()) out.append("\n\n")
            out.append("## ").append(sheet.name.ifBlank { "Sheet" }).append("\n\n").append(table)
        }
        if (sheets.size > caps.maxSheets) truncatedSheets = true
        if (truncatedSheets) {
            out.append("\n\n")
            out.append("Workbook truncated to ${caps.maxSheets} sheets.")
        }
        val markdown = out.toString().trim()
        return markdown.ifBlank { null }
    }

    private data class SheetRef(val name: String, val path: String)

    private fun sheetList(entries: Map<String, ByteArray>): List<SheetRef> {
        val rels = workbookRels(entries["xl/_rels/workbook.xml.rels"])
        val declared = workbookSheets(entries["xl/workbook.xml"], rels)
        if (declared.isNotEmpty()) return declared
        return entries.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sorted()
            .mapIndexed { index, path -> SheetRef("Sheet ${index + 1}", path) }
    }

    private fun workbookSheets(bytes: ByteArray?, rels: Map<String, String>): List<SheetRef> {
        if (bytes == null) return emptyList()
        val reader = xmlReader(bytes)
        val sheets = mutableListOf<SheetRef>()
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.localName == "sheet") {
                val name = attribute(reader, "name").orEmpty()
                val id = relationshipId(reader) ?: continue
                val target = rels[id] ?: continue
                sheets += SheetRef(name, resolveSheet(target))
            }
        }
        reader.close()
        return sheets
    }

    private fun workbookRels(bytes: ByteArray?): Map<String, String> {
        if (bytes == null) return emptyMap()
        val reader = xmlReader(bytes)
        val rels = mutableMapOf<String, String>()
        while (reader.hasNext()) {
            if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.localName == "Relationship") {
                val id = attribute(reader, "Id") ?: continue
                val target = attribute(reader, "Target") ?: continue
                rels[id] = target
            }
        }
        reader.close()
        return rels
    }

    private fun resolveSheet(target: String): String {
        val cleaned = target.trim().removePrefix("/")
        return if (cleaned.startsWith("xl/")) cleaned else "xl/${cleaned.removePrefix("../")}"
    }

    private fun sharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val reader = xmlReader(bytes)
        val strings = mutableListOf<String>()
        val current = StringBuilder()
        var inItem = false
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                    "si" -> {
                        inItem = true
                        current.clear()
                    }
                    "t" -> if (inItem) current.append(elementText(reader))
                }
                XMLStreamConstants.END_ELEMENT -> if (reader.localName == "si") {
                    strings += current.toString()
                    inItem = false
                }
            }
        }
        reader.close()
        return strings
    }

    private fun sheetRows(xml: ByteArray, shared: List<String>, caps: TableCaps): List<List<String>> {
        val reader = xmlReader(xml)
        val rows = mutableListOf<List<String>>()
        var cells = mutableMapOf<Int, String>()
        var inCell = false
        var cellType = ""
        var cellRef = ""
        var cellValue = ""
        while (reader.hasNext()) {
            when (reader.next()) {
                XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                    "c" -> {
                        inCell = true
                        cellType = attribute(reader, "t").orEmpty()
                        cellRef = attribute(reader, "r").orEmpty()
                        cellValue = ""
                    }
                    "v" -> if (inCell) cellValue = elementText(reader)
                    "t" -> if (inCell && cellType == "inlineStr") cellValue += elementText(reader)
                }
                XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
                    "c" -> {
                        val column = columnIndex(cellRef)
                        if (column >= caps.maxCols) {
                            if (caps.maxCols !in cells) cells[caps.maxCols] = ""
                        } else if (column >= 0) {
                            cells[column] = cellText(cellType, cellValue, shared)
                        }
                        inCell = false
                    }
                    "row" -> {
                        if (rows.size < caps.maxRows + 2) {
                            val width = (cells.keys.maxOrNull() ?: -1) + 1
                            rows += (0 until width).map { cells[it].orEmpty() }
                        }
                        cells = mutableMapOf()
                    }
                }
            }
        }
        reader.close()
        return rows
    }

    private fun cellText(type: String, value: String, shared: List<String>): String = when (type) {
        "s" -> shared.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
        "inlineStr", "str" -> value
        "b" -> if (value == "1") "TRUE" else "FALSE"
        else -> value
    }

    private fun columnIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }
        if (letters.isEmpty()) return 0
        var value = 0
        letters.forEach { char -> value = value * 26 + (char.uppercaseChar() - 'A' + 1) }
        return value - 1
    }

    private fun relationshipId(reader: XMLStreamReader): String? {
        var fallback: String? = null
        for (index in 0 until reader.attributeCount) {
            val local = reader.getAttributeLocalName(index)
            val value = reader.getAttributeValue(index) ?: continue
            if (local == "id" && value.startsWith("rId")) return value
            if (local == "id") fallback = value
        }
        return fallback
    }
}

private object ZipBytes {
    fun read(bytes: ByteArray): Map<String, ByteArray>? = try {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val path = ArchivePaths.normalize(entry.name)
                if (!entry.isDirectory && path != null) {
                    val payload = ProjectArchiveZip.readCapped(zip, ProjectArchiveZip.MAX_ENTRY_BYTES)
                    if (payload != null) entries[path] = payload
                }
                zip.closeEntry()
            }
        }
        entries
    } catch (_: Exception) {
        null
    }
}

private fun xmlReader(bytes: ByteArray): XMLStreamReader {
    val factory = XMLInputFactory.newFactory()
    try {
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
    } catch (_: Exception) {
        // Some parsers reject the property and still refuse external entities by default.
    }
    try {
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    } catch (_: Exception) {
        // See above.
    }
    return factory.createXMLStreamReader(ByteArrayInputStream(bytes))
}

private fun attribute(reader: XMLStreamReader, localName: String): String? {
    for (index in 0 until reader.attributeCount) {
        if (reader.getAttributeLocalName(index) == localName) return reader.getAttributeValue(index)
    }
    return null
}

private fun elementText(reader: XMLStreamReader): String = try {
    reader.elementText ?: ""
} catch (_: Exception) {
    ""
}
