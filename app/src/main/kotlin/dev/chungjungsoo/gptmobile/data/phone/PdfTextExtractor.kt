package dev.chungjungsoo.gptmobile.data.phone

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Lightweight PDF text extraction for shared documents.
 *
 * Reads uncompressed content streams and FlateDecode streams, then collects
 * text-showing operators (`Tj`, `TJ`, `'`, `"`). Image-only and scanned PDFs
 * return an empty string so the caller can fall back to rendered pages.
 */
object PdfTextExtractor {
    const val MAX_CHARS = 24_000
    private const val MAX_STREAMS = 40
    private const val MAX_INFLATED_BYTES = 2 * 1024 * 1024
    private const val MAX_INPUT_BYTES = 30 * 1024 * 1024

    fun extract(bytes: ByteArray, maxChars: Int = MAX_CHARS): String {
        if (bytes.isEmpty() || bytes.size > MAX_INPUT_BYTES || maxChars <= 0) return ""
        val latin = bytes.toString(Charsets.ISO_8859_1)
        val output = StringBuilder()
        var streams = 0
        for (stream in contentStreams(latin)) {
            if (streams++ >= MAX_STREAMS) break
            val text = extractOperators(stream)
            if (text.isBlank()) continue
            if (output.isNotEmpty() && !output.last().isWhitespace()) output.append('\n')
            output.append(text.trim())
            if (output.length >= maxChars) break
        }
        return output.toString().take(maxChars).trim()
    }

    private fun contentStreams(pdf: String): List<String> {
        val streams = mutableListOf<String>()
        var searchFrom = 0
        while (searchFrom < pdf.length && streams.size < MAX_STREAMS) {
            val streamAt = indexOfStreamKeyword(pdf, searchFrom)
            if (streamAt < 0) break
            val dictStart = pdf.lastIndexOf("<<", streamAt).coerceAtLeast(0)
            val dict = pdf.substring(dictStart, streamAt)
            var dataStart = streamAt + "stream".length
            if (pdf.getOrNull(dataStart) == '\r') dataStart++
            if (pdf.getOrNull(dataStart) == '\n') dataStart++
            val length = Regex("""/Length\s+(\d+)""").find(dict)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val dataEnd = if (length != null && length >= 0 && dataStart + length <= pdf.length) {
                dataStart + length
            } else {
                val end = pdf.indexOf("endstream", dataStart)
                if (end < 0) break else end
            }
            val raw = pdf.substring(dataStart, dataEnd).trimEnd('\r', '\n').toByteArray(Charsets.ISO_8859_1)
            val decoded = decodeStream(dict, raw)
            if (decoded != null) streams += decoded
            searchFrom = dataEnd
        }
        return streams
    }

    private fun indexOfStreamKeyword(pdf: String, from: Int): Int {
        var index = from
        while (index < pdf.length) {
            val found = pdf.indexOf("stream", index)
            if (found < 0) return -1
            val isEndStream = found >= 3 && pdf.regionMatches(found - 3, "end", 0, 3)
            if (!isEndStream) return found
            index = found + "stream".length
        }
        return -1
    }

    private fun decodeStream(dict: String, raw: ByteArray): String? {
        val predictor = Regex("""/Predictor\s+(\d+)""").find(dict)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        if (predictor > 1) return null
        val inflated = if (dict.contains("/FlateDecode")) inflate(raw) ?: return null else raw
        val text = inflated.toString(Charsets.ISO_8859_1)
        if (!text.contains("Tj") && !text.contains("TJ") && !text.contains("'") && !text.contains("\"")) return null
        return text
    }

    private fun inflate(raw: ByteArray): ByteArray? = try {
        val inflater = Inflater()
        inflater.setInput(raw)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (!inflater.finished() && total < MAX_INFLATED_BYTES) {
            val count = inflater.inflate(buffer)
            if (count == 0 && inflater.needsInput()) break
            if (count == 0) break
            output.write(buffer, 0, count)
            total += count
        }
        inflater.end()
        if (total == 0) null else output.toByteArray()
    } catch (_: Exception) {
        null
    }

    private fun extractOperators(content: String): String {
        val output = StringBuilder()
        var lastText: String? = null
        var index = 0
        while (index < content.length) {
            val char = content[index]
            when {
                char.isWhitespace() -> index++
                char == '%' -> {
                    val newline = content.indexOf('\n', index)
                    index = if (newline < 0) content.length else newline + 1
                }
                char == '(' -> {
                    val (text, next) = readLiteral(content, index)
                    lastText = text
                    index = next
                }
                char == '<' && content.getOrNull(index + 1) != '<' -> {
                    val (text, next) = readHex(content, index)
                    lastText = text
                    index = next
                }
                char == '[' -> {
                    val (text, next) = readArray(content, index)
                    lastText = text
                    index = next
                }
                char == '/' -> {
                    index++
                    while (index < content.length && !content[index].isWhitespace() && content[index] !in "()<>[]{}/%") {
                        index++
                    }
                }
                char.isLetter() || char == '\'' || char == '"' -> {
                    val operator = readOperator(content, index)
                    when (operator.name) {
                        "Tj", "TJ" -> appendPiece(output, lastText)
                        "'", "\"" -> {
                            if (output.isNotEmpty()) output.append('\n')
                            appendPiece(output, lastText)
                        }
                        "T*", "Td", "TD" -> if (output.isNotEmpty() && output.last() != '\n') output.append('\n')
                    }
                    lastText = null
                    index = operator.next
                }
                else -> index++
            }
        }
        return output.toString()
    }

    private fun appendPiece(output: StringBuilder, text: String?) {
        if (text.isNullOrEmpty()) return
        if (output.isNotEmpty() && !output.last().isWhitespace() && text.first().isLetterOrDigit()) {
            output.append(' ')
        }
        output.append(text)
    }

    private data class Operator(val name: String, val next: Int)

    private fun readOperator(content: String, start: Int): Operator {
        val char = content[start]
        if (char == '\'' || char == '"') return Operator(char.toString(), start + 1)
        var index = start
        while (index < content.length && content[index].isLetter()) index++
        return Operator(content.substring(start, index), index)
    }

    private fun readLiteral(content: String, start: Int): Pair<String, Int> {
        val output = StringBuilder()
        var index = start + 1
        var depth = 1
        while (index < content.length && depth > 0) {
            val char = content[index]
            if (char == '\\' && index + 1 < content.length) {
                when (val escaped = content[index + 1]) {
                    'n' -> {
                        output.append('\n')
                        index += 2
                    }
                    'r' -> {
                        output.append('\r')
                        index += 2
                    }
                    't' -> {
                        output.append('\t')
                        index += 2
                    }
                    'b' -> {
                        output.append('\b')
                        index += 2
                    }
                    'f' -> {
                        output.append('\u000c')
                        index += 2
                    }
                    '(', ')', '\\' -> {
                        output.append(escaped)
                        index += 2
                    }
                    '\n' -> index += 2
                    '\r' -> {
                        index += 2
                        if (content.getOrNull(index) == '\n') index++
                    }
                    else -> {
                        if (escaped in '0'..'7') {
                            var octal = 0
                            var count = 0
                            var cursor = index + 1
                            while (cursor < content.length && count < 3 && content[cursor] in '0'..'7') {
                                octal = octal * 8 + (content[cursor] - '0')
                                cursor++
                                count++
                            }
                            output.append(octal.toChar())
                            index = cursor
                        } else {
                            output.append(escaped)
                            index += 2
                        }
                    }
                }
            } else if (char == '(') {
                depth++
                output.append(char)
                index++
            } else if (char == ')') {
                depth--
                if (depth > 0) output.append(char)
                index++
            } else {
                output.append(char)
                index++
            }
        }
        return output.toString() to index
    }

    private fun readHex(content: String, start: Int): Pair<String, Int> {
        val end = content.indexOf('>', start + 1)
        if (end < 0) return "" to content.length
        val hex = content.substring(start + 1, end).filterNot { it.isWhitespace() }
        val padded = if (hex.length % 2 == 1) hex + "0" else hex
        val bytes = ByteArray(padded.length / 2)
        for (offset in bytes.indices) {
            val pair = padded.substring(offset * 2, offset * 2 + 2)
            bytes[offset] = pair.toIntOrNull(16)?.toByte() ?: 0
        }
        val text = if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            bytes.decodeToString(2, bytes.size, Charsets.UTF_16BE)
        } else {
            bytes.toString(Charsets.ISO_8859_1)
        }
        return text to end + 1
    }

    private fun ByteArray.decodeToString(start: Int, end: Int, charset: java.nio.charset.Charset): String =
        String(this, start, end - start, charset)

    private fun readArray(content: String, start: Int): Pair<String, Int> {
        val output = StringBuilder()
        var index = start + 1
        while (index < content.length && content[index] != ']') {
            when {
                content[index].isWhitespace() -> index++
                content[index] == '(' -> {
                    val (text, next) = readLiteral(content, index)
                    appendPiece(output, text)
                    index = next
                }
                content[index] == '<' && content.getOrNull(index + 1) != '<' -> {
                    val (text, next) = readHex(content, index)
                    appendPiece(output, text)
                    index = next
                }
                content[index] == '-' || content[index] == '+' || content[index].isDigit() || content[index] == '.' -> {
                    val begin = index
                    if (content[index] == '+' || content[index] == '-') index++
                    while (index < content.length && (content[index].isDigit() || content[index] == '.')) index++
                    val number = content.substring(begin, index).toDoubleOrNull() ?: 0.0
                    if (number <= -80 && output.isNotEmpty() && !output.last().isWhitespace()) {
                        output.append(' ')
                    }
                }
                else -> index++
            }
        }
        return output.toString() to if (index < content.length) index + 1 else index
    }
}
