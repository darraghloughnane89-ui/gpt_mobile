package dev.chungjungsoo.gptmobile.data.attachment

import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

data class DocumentTextLimits(
    val maxBytes: Long = DEFAULT_MAX_BYTES,
    val maxCharacters: Int = DEFAULT_MAX_CHARACTERS,
    val chunkCharacters: Int = DEFAULT_CHUNK_CHARACTERS
) {
    companion object {
        const val DEFAULT_MAX_BYTES = 20L * 1024 * 1024
        const val DEFAULT_MAX_CHARACTERS = 80_000
        const val DEFAULT_CHUNK_CHARACTERS = 8_000
        val Default = DocumentTextLimits()
    }
}

class DocumentExtractionException(message: String) : IllegalArgumentException(message)

sealed class DocumentExtraction {
    data class Ready(
        val contextText: String,
        val truncated: Boolean,
        val chunkCount: Int
    ) : DocumentExtraction()

    data class Rejected(val reason: String) : DocumentExtraction()
}

object DocumentTextExtractor {
    fun extract(
        bytes: ByteArray,
        mimeType: String,
        displayName: String,
        limits: DocumentTextLimits = DocumentTextLimits.Default
    ): DocumentExtraction {
        if (bytes.isEmpty()) return DocumentExtraction.Rejected("The file is empty.")
        if (bytes.size.toLong() > limits.maxBytes) {
            return DocumentExtraction.Rejected("Documents larger than ${limits.maxBytes / (1024 * 1024)} MB cannot be attached.")
        }
        val raw = when {
            isPlainText(mimeType, displayName) -> decodePlainText(bytes)
            isPdf(mimeType, displayName) -> extractPdfText(bytes)
            isDocx(mimeType, displayName) -> extractDocxText(bytes)
            isXlsx(mimeType, displayName) -> extractXlsxText(bytes)
            else -> return DocumentExtraction.Rejected("Only images, PDF, Word, Excel, and text files can be attached.")
        }
        if (raw.isBlank()) {
            return DocumentExtraction.Rejected("No extractable text was found in $displayName.")
        }
        return chunk(displayName, raw, limits)
    }

    fun chunk(
        displayName: String,
        text: String,
        limits: DocumentTextLimits = DocumentTextLimits.Default
    ): DocumentExtraction.Ready {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        val truncated = normalized.length > limits.maxCharacters
        val capped = if (truncated) normalized.take(limits.maxCharacters) else normalized
        val chunks = splitChunks(capped, limits.chunkCharacters.coerceAtLeast(1))
        val context = buildString {
            append("[Attached file: ").append(displayName).append("]\n")
            chunks.forEachIndexed { index, chunk ->
                append("[Part ").append(index + 1).append('/').append(chunks.size).append("]\n")
                append(chunk.trim()).append("\n\n")
            }
            if (truncated) {
                append("[Truncated to ").append(limits.maxCharacters).append(" characters.]\n")
            }
        }.trim()
        return DocumentExtraction.Ready(
            contextText = context,
            truncated = truncated,
            chunkCount = chunks.size
        )
    }

    private fun splitChunks(text: String, chunkCharacters: Int): List<String> {
        if (text.length <= chunkCharacters) return listOf(text)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        text.split("\n\n").forEach { paragraph ->
            val piece = paragraph.trim()
            if (piece.isEmpty()) return@forEach
            if (piece.length > chunkCharacters) {
                if (current.isNotEmpty()) {
                    chunks += current.toString().trim()
                    current.clear()
                }
                var start = 0
                while (start < piece.length) {
                    val end = (start + chunkCharacters).coerceAtMost(piece.length)
                    chunks += piece.substring(start, end).trim()
                    start = end
                }
                return@forEach
            }
            val separator = if (current.isEmpty()) 0 else 2
            if (current.length + separator + piece.length > chunkCharacters) {
                chunks += current.toString().trim()
                current.clear()
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(piece)
        }
        if (current.isNotEmpty()) chunks += current.toString().trim()
        return chunks.ifEmpty { listOf(text.take(chunkCharacters)) }
    }

    private fun isPlainText(mimeType: String, displayName: String): Boolean =
        mimeType == "text/plain" || displayName.substringAfterLast('.', "").equals("txt", ignoreCase = true)

    private fun isPdf(mimeType: String, displayName: String): Boolean =
        mimeType == "application/pdf" || displayName.substringAfterLast('.', "").equals("pdf", ignoreCase = true)

    private fun isDocx(mimeType: String, displayName: String): Boolean =
        mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            displayName.substringAfterLast('.', "").equals("docx", ignoreCase = true)

    private fun isXlsx(mimeType: String, displayName: String): Boolean =
        mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
            displayName.substringAfterLast('.', "").equals("xlsx", ignoreCase = true)

    private fun decodePlainText(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.copyOfRange(3, bytes.size).toString(StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        return bytes.toString(StandardCharsets.UTF_8)
    }

    private fun extractPdfText(bytes: ByteArray): String {
        val raw = bytes.toString(StandardCharsets.ISO_8859_1)
        val pieces = mutableListOf<String>()
        var index = 0
        while (index < raw.length) {
            val streamAt = raw.indexOf("stream", index)
            if (streamAt < 0) break
            var dataStart = streamAt + "stream".length
            if (dataStart < raw.length && raw[dataStart] == '\r') dataStart += 1
            if (dataStart < raw.length && raw[dataStart] == '\n') dataStart += 1
            val end = raw.indexOf("endstream", dataStart)
            if (end < 0) break
            val dictStart = raw.lastIndexOf("<<", streamAt).coerceAtLeast(0)
            val dictionary = raw.substring(dictStart, streamAt)
            var data = raw.substring(dataStart, end).trimEnd('\r', '\n').toByteArray(StandardCharsets.ISO_8859_1)
            if (dictionary.contains("/FlateDecode")) {
                inflate(data)?.let { data = it }
            }
            val extracted = extractPdfShowStrings(data.toString(StandardCharsets.ISO_8859_1))
            if (extracted.isNotBlank()) pieces += extracted
            index = end + "endstream".length
        }
        return pieces.joinToString("\n")
    }

    private fun extractPdfShowStrings(content: String): String {
        val pieces = mutableListOf<String>()
        var index = 0
        while (index < content.length) {
            when (content[index]) {
                '(' -> {
                    val literal = readPdfLiteral(content, index)
                    if (followedByTextShow(content, literal.next)) pieces += literal.text
                    index = literal.next
                }

                '<' -> {
                    if (index + 1 < content.length && content[index + 1] != '<') {
                        val hex = readPdfHex(content, index)
                        if (followedByTextShow(content, hex.next)) pieces += hex.text
                        index = hex.next
                    } else {
                        index += 1
                    }
                }

                else -> index += 1
            }
        }
        return pieces.joinToString("")
    }

    private fun followedByTextShow(content: String, start: Int): Boolean {
        var index = start
        var scanned = 0
        while (index < content.length && scanned < 64) {
            val character = content[index]
            if (character == '(') return true
            if (character.isWhitespace() || character.isDigit() || character == '.' || character == '-' || character == '[' || character == ']') {
                index += 1
                scanned += 1
                continue
            }
            return content.startsWith("Tj", index) ||
                content.startsWith("TJ", index) ||
                character == '\'' ||
                character == '"'
        }
        return false
    }

    private data class PdfString(val text: String, val next: Int)

    private fun readPdfLiteral(content: String, start: Int): PdfString {
        val text = StringBuilder()
        var index = start + 1
        var depth = 1
        while (index < content.length && depth > 0) {
            val character = content[index]
            if (character == '\\' && index + 1 < content.length) {
                val escaped = content[index + 1]
                when (escaped) {
                    'n' -> text.append('\n')
                    'r' -> text.append('\r')
                    't' -> text.append('\t')
                    'b' -> text.append('\b')
                    'f' -> text.append('\u000C')
                    '(', ')', '\\' -> text.append(escaped)
                    else -> {
                        if (escaped.isDigit()) {
                            val octal = StringBuilder().append(escaped)
                            var next = index + 2
                            repeat(2) {
                                if (next < content.length && content[next].isDigit()) {
                                    octal.append(content[next])
                                    next += 1
                                }
                            }
                            text.append(octal.toString().toInt(8).toChar())
                            index = next
                            continue
                        }
                        text.append(escaped)
                    }
                }
                index += 2
                continue
            }
            when (character) {
                '(' -> {
                    depth += 1
                    text.append(character)
                }

                ')' -> {
                    depth -= 1
                    if (depth > 0) text.append(character)
                }

                else -> text.append(character)
            }
            index += 1
        }
        return PdfString(text.toString(), index)
    }

    private fun readPdfHex(content: String, start: Int): PdfString {
        val end = content.indexOf('>', start + 1)
        if (end < 0) return PdfString("", start + 1)
        val hex = content.substring(start + 1, end).filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        val padded = if (hex.length % 2 == 0) hex else hex + "0"
        val bytes = ByteArray(padded.length / 2)
        for (offset in bytes.indices) {
            bytes[offset] = padded.substring(offset * 2, offset * 2 + 2).toInt(16).toByte()
        }
        val text = if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        } else {
            bytes.toString(StandardCharsets.ISO_8859_1)
        }
        return PdfString(text, end + 1)
    }

    private fun inflate(data: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(data)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var guard = 0
            while (!inflater.finished() && guard < 10_000) {
                val count = inflater.inflate(buffer)
                if (count == 0) break
                output.write(buffer, 0, count)
                guard += 1
            }
            output.toByteArray().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun extractDocxText(bytes: ByteArray): String {
        val document = zipEntry(bytes, "word/document.xml") ?: return ""
        return xmlText(document, "w:p", "w:t")
    }

    private fun extractXlsxText(bytes: ByteArray): String {
        val shared = zipEntry(bytes, "xl/sharedStrings.xml")?.let(::sharedStrings).orEmpty()
        val sheets = zipEntries(bytes) { name ->
            name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")
        }.sortedBy { it.first }
        if (sheets.isEmpty()) return shared.joinToString("\n")
        return sheets.joinToString("\n") { (_, xml) -> sheetText(xml, shared) }.trim()
    }

    private fun sharedStrings(xml: String): List<String> {
        val values = mutableListOf<String>()
        var index = 0
        while (index < xml.length) {
            val item = xml.indexOf("<si", index)
            if (item < 0) break
            val end = xml.indexOf("</si>", item)
            if (end < 0) break
            values += xmlTextNodes(xml.substring(item, end), "t")
            index = end + 5
        }
        return values
    }

    private fun sheetText(xml: String, shared: List<String>): String {
        val rows = mutableListOf<String>()
        var index = 0
        while (index < xml.length) {
            val rowAt = xml.indexOf("<row", index)
            if (rowAt < 0) break
            val rowEnd = xml.indexOf("</row>", rowAt)
            if (rowEnd < 0) break
            val row = xml.substring(rowAt, rowEnd)
            val cells = mutableListOf<String>()
            var cellAt = 0
            while (cellAt < row.length) {
                val cellStart = row.indexOf("<c", cellAt)
                if (cellStart < 0) break
                val cellEnd = row.indexOf("</c>", cellStart).takeIf { it >= 0 } ?: row.indexOf("/>", cellStart)
                if (cellEnd < 0) break
                val cell = row.substring(cellStart, cellEnd)
                val type = xmlAttribute(cell, "t")
                val value = when (type) {
                    "s" -> shared.getOrNull(xmlTagValue(cell, "v").toIntOrNull() ?: -1).orEmpty()
                    "inlineStr" -> xmlTextNodes(cell, "t")
                    else -> xmlTagValue(cell, "v")
                }
                if (value.isNotBlank()) cells += value
                cellAt = cellEnd + 4
            }
            if (cells.isNotEmpty()) rows += cells.joinToString("\t")
            index = rowEnd + 6
        }
        return rows.joinToString("\n")
    }

    private fun xmlText(xml: String, blockTag: String, textTag: String): String {
        val blocks = mutableListOf<String>()
        var index = 0
        val open = "<$blockTag"
        val close = "</$blockTag>"
        while (index < xml.length) {
            val start = xml.indexOf(open, index)
            if (start < 0) break
            val end = xml.indexOf(close, start)
            if (end < 0) break
            val text = xmlTextNodes(xml.substring(start, end), textTag)
            if (text.isNotBlank()) blocks += text
            index = end + close.length
        }
        return blocks.joinToString("\n")
    }

    private fun xmlTextNodes(xml: String, tag: String): String {
        val local = tag.substringAfter(':')
        val pieces = mutableListOf<String>()
        var index = 0
        while (index < xml.length) {
            val start = xml.indexOf("<$tag", index).takeIf { it >= 0 }
                ?: xml.indexOf("<$local", index)
            if (start < 0) break
            val tagEnd = xml.indexOf('>', start)
            if (tagEnd < 0) break
            if (xml[tagEnd - 1] == '/') {
                index = tagEnd + 1
                continue
            }
            val close = xml.indexOf("</", tagEnd)
            if (close < 0) break
            pieces += decodeXml(xml.substring(tagEnd + 1, close))
            index = close + 2
        }
        return pieces.joinToString("")
    }

    private fun xmlTagValue(xml: String, tag: String): String {
        val start = xml.indexOf("<$tag")
        if (start < 0) return ""
        val tagEnd = xml.indexOf('>', start)
        if (tagEnd < 0 || xml[tagEnd - 1] == '/') return ""
        val close = xml.indexOf("</$tag>", tagEnd)
        if (close < 0) return ""
        return decodeXml(xml.substring(tagEnd + 1, close)).trim()
    }

    private fun xmlAttribute(xml: String, name: String): String? {
        val marker = "$name=\""
        val start = xml.indexOf(marker)
        if (start < 0) return null
        val valueStart = start + marker.length
        val end = xml.indexOf('"', valueStart)
        if (end < 0) return null
        return xml.substring(valueStart, end)
    }

    private fun decodeXml(value: String): String = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    private fun zipEntry(bytes: ByteArray, name: String): String? =
        zipEntries(bytes) { it == name }.firstOrNull()?.second

    private fun zipEntries(bytes: ByteArray, include: (String) -> Boolean): List<Pair<String, String>> {
        val entries = mutableListOf<Pair<String, String>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && include(entry.name)) {
                    entries += entry.name to zip.readBytes().toString(StandardCharsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }
}

fun modelContextText(content: String, attachments: List<ChatAttachment>): String {
    val documents = attachments.mapNotNull { attachment -> attachment.extractedText?.takeIf { it.isNotBlank() } }
    if (documents.isEmpty()) return content
    return buildString {
        documents.forEach { document ->
            append(document.trim())
            append("\n\n")
        }
        append(content)
    }.trim()
}
