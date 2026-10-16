package dev.chungjungsoo.gptmobile.data.agent.tool.code

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader

internal object SpreadsheetText {
    const val MAX_CSV_BYTES = 256 * 1024
    const val MAX_XLSX_BYTES = 1024 * 1024
    const val MAX_ROWS = 200
    const val MAX_COLUMNS = 32
    private const val MAX_CELL_CHARS = 500
    private const val MAX_ZIP_ENTRIES = 64

    fun isTabular(name: String, mimeType: String): Boolean {
        val fileName = name.lowercase()
        val mime = mimeType.lowercase()
        return isWorkbook(fileName, mime) ||
            mime == "text/csv" ||
            mime == "text/tab-separated-values" ||
            fileName.endsWith(".csv") ||
            fileName.endsWith(".tsv")
    }

    fun isWorkbook(name: String, mimeType: String): Boolean {
        val fileName = name.lowercase()
        val mime = mimeType.lowercase()
        return fileName.endsWith(".xlsx") || mime.contains("spreadsheetml.sheet")
    }

    fun extract(name: String, mimeType: String, bytes: ByteArray): String? {
        if (!isTabular(name, mimeType)) return null
        return if (isWorkbook(name, mimeType)) workbookCsv(bytes) else decodeDelimited(bytes)
    }

    fun parseCsv(text: String): List<List<String>> {
        if (text.length > MAX_CSV_BYTES) throw IllegalArgumentException("CSV is too large.")
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                quoted && char == '"' && text.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index += 1
                }

                quoted && char == '"' -> quoted = false

                quoted -> cell.append(char)

                char == '"' && cell.isEmpty() -> quoted = true

                char == ',' -> pushCell(row, cell)

                char == '\n' || char == '\r' -> {
                    if (char == '\r' && text.getOrNull(index + 1) == '\n') index += 1
                    pushCell(row, cell)
                    if (rows.size >= MAX_ROWS) throw IllegalArgumentException("CSV exceeds $MAX_ROWS rows.")
                    rows += row.toList()
                    row.clear()
                }

                else -> cell.append(char)
            }
            index += 1
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            pushCell(row, cell)
            if (rows.size >= MAX_ROWS) throw IllegalArgumentException("CSV exceeds $MAX_ROWS rows.")
            rows += row.toList()
        }
        return rows
    }

    private fun pushCell(row: MutableList<String>, cell: StringBuilder) {
        if (row.size >= MAX_COLUMNS) throw IllegalArgumentException("CSV exceeds $MAX_COLUMNS columns.")
        row += cell.toString().take(MAX_CELL_CHARS)
        cell.clear()
    }

    private fun decodeDelimited(bytes: ByteArray): String {
        val limited = if (bytes.size > MAX_CSV_BYTES) bytes.copyOf(MAX_CSV_BYTES) else bytes
        return String(limited, StandardCharsets.UTF_8).removePrefix("\uFEFF")
    }

    private fun workbookCsv(bytes: ByteArray): String? {
        if (bytes.size > MAX_XLSX_BYTES) return null
        val entries = readWorkbookEntries(bytes) ?: return null
        val shared = entries.sharedStrings?.let(::sharedStrings).orEmpty()
        val sheet = entries.sheet ?: return null
        val rows = sheetRows(sheet, shared) ?: return null
        if (rows.isEmpty()) return ""
        return rows.joinToString("\n") { row -> row.joinToString(",") { cell -> csvCell(cell) } }
    }

    private fun csvCell(value: String): String {
        val limited = value.take(MAX_CELL_CHARS)
        val needsQuotes = limited.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return limited
        return "\"" + limited.replace("\"", "\"\"") + "\""
    }

    private data class WorkbookEntries(val sharedStrings: ByteArray?, val sheet: ByteArray?)

    private fun readWorkbookEntries(bytes: ByteArray): WorkbookEntries? = try {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var shared: ByteArray? = null
            var sheet: ByteArray? = null
            var scanned = 0
            while (scanned < MAX_ZIP_ENTRIES) {
                val entry = zip.nextEntry ?: break
                scanned += 1
                val name = entry.name.replace('\\', '/').lowercase()
                if (name.contains("../")) {
                    zip.closeEntry()
                    continue
                }
                when {
                    name == "xl/sharedstrings.xml" && shared == null -> shared = readEntry(zip)

                    name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") -> {
                        val body = readEntry(zip)
                        if (body != null && (sheet == null || name.endsWith("sheet1.xml"))) sheet = body
                    }
                }
                zip.closeEntry()
            }
            WorkbookEntries(shared, sheet)
        }
    } catch (ignored: Exception) {
        null
    }

    private fun readEntry(zip: ZipInputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (total < MAX_XLSX_BYTES) {
            val read = zip.read(chunk, 0, minOf(chunk.size, MAX_XLSX_BYTES - total))
            if (read < 0) return buffer.toByteArray()
            buffer.write(chunk, 0, read)
            total += read
        }
        return if (zip.read() < 0) buffer.toByteArray() else null
    }

    private fun sharedStrings(bytes: ByteArray): List<String>? = try {
        val reader = xmlReader(bytes)
        val strings = mutableListOf<String>()
        val current = StringBuilder()
        var inItem = false
        var inText = false
        try {
            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                        "si" -> {
                            inItem = true
                            current.clear()
                        }

                        "t" -> if (inItem) inText = true
                    }

                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> if (inText) current.append(reader.text)

                    XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
                        "t" -> inText = false

                        "si" -> {
                            strings += current.toString().take(MAX_CELL_CHARS)
                            inItem = false
                            inText = false
                        }
                    }
                }
            }
        } finally {
            reader.close()
        }
        strings
    } catch (ignored: Exception) {
        null
    }

    private fun sheetRows(bytes: ByteArray, shared: List<String>): List<List<String>>? = try {
        val reader = xmlReader(bytes)
        val rows = linkedMapOf<Int, MutableMap<Int, String>>()
        var cellRef = ""
        var cellType = ""
        var inCell = false
        var inValue = false
        var inline = false
        val value = StringBuilder()
        try {
            while (reader.hasNext() && rows.size <= MAX_ROWS) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> when (reader.localName) {
                        "c" -> {
                            inCell = true
                            cellRef = attribute(reader, "r").orEmpty()
                            cellType = attribute(reader, "t").orEmpty()
                            inline = false
                            value.clear()
                        }

                        "v" -> if (inCell) inValue = true

                        "is" -> if (inCell) inline = true

                        "t" -> if (inCell && inline) inValue = true
                    }

                    XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> if (inValue) value.append(reader.text)

                    XMLStreamConstants.END_ELEMENT -> when (reader.localName) {
                        "v", "t" -> inValue = false

                        "c" -> {
                            val rowIndex = rowNumber(cellRef)
                            val column = columnIndex(cellRef)
                            if (rowIndex in 1..MAX_ROWS && column in 0 until MAX_COLUMNS) {
                                rows.getOrPut(rowIndex) { linkedMapOf() }[column] = cellValue(cellType, value.toString(), shared)
                            }
                            inCell = false
                        }
                    }
                }
            }
        } finally {
            reader.close()
        }
        val ordered = rows.toSortedMap()
        val width = ordered.values.maxOfOrNull { cells -> cells.keys.maxOrNull() ?: -1 }?.plus(1) ?: 0
        ordered.values.map { cells ->
            List(width.coerceAtMost(MAX_COLUMNS)) { index -> cells[index].orEmpty() }
        }
    } catch (ignored: Exception) {
        null
    }

    private fun cellValue(type: String, raw: String, shared: List<String>): String = when (type) {
        "s" -> shared.getOrNull(raw.trim().toIntOrNull() ?: -1).orEmpty()
        "inlineStr", "str" -> raw
        "b" -> if (raw.trim() == "1") "true" else "false"
        "e" -> ""
        else -> raw.trim()
    }.take(MAX_CELL_CHARS)

    private fun rowNumber(cellRef: String): Int = cellRef.takeLastWhile { it.isDigit() }.toIntOrNull() ?: 1

    private fun columnIndex(cellRef: String): Int {
        var index = 0
        for (char in cellRef) {
            if (!char.isLetter()) break
            index = index * 26 + (char.uppercaseChar() - 'A' + 1)
        }
        return index - 1
    }

    private fun xmlReader(bytes: ByteArray): XMLStreamReader {
        val factory = XMLInputFactory.newFactory()
        runCatching { factory.setProperty(XMLInputFactory.SUPPORT_DTD, false) }
        runCatching { factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false) }
        return factory.createXMLStreamReader(ByteArrayInputStream(bytes))
    }

    private fun attribute(reader: XMLStreamReader, name: String): String? {
        for (index in 0 until reader.attributeCount) {
            if (reader.getAttributeLocalName(index) == name) return reader.getAttributeValue(index)
        }
        return null
    }
}
