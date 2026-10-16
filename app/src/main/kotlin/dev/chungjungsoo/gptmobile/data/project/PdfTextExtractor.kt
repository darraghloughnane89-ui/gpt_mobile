package dev.chungjungsoo.gptmobile.data.project

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Pulls visible text out of simple PDF content streams, including FlateDecode.
 * Encoded fonts and ToUnicode maps are left as-is, so some PDFs yield little or no text.
 */
object PdfTextExtractor {
    private const val MAX_INFLATED_BYTES = 2_000_000

    fun extract(bytes: ByteArray, maxChars: Int): String {
        if (maxChars <= 0 || bytes.isEmpty()) return ""
        val latin = bytes.toString(Charsets.ISO_8859_1)
        val parts = ArrayList<String>()
        var cursor = 0
        var collected = 0
        while (cursor < latin.length && collected < maxChars) {
            val streamAt = nextStream(latin, cursor) ?: break
            val dataStart = skipLineBreak(latin, streamAt + "stream".length)
            val end = latin.indexOf("endstream", dataStart)
            if (end < 0) break
            val dataEnd = trimLineBreakBefore(latin, dataStart, end)
            val headerStart = (streamAt - 500).coerceAtLeast(0)
            val header = latin.substring(headerStart, streamAt)
            val raw = latin.substring(dataStart, dataEnd).toByteArray(Charsets.ISO_8859_1)
            val decoded = if (header.contains("FlateDecode")) inflate(raw) ?: raw else raw
            val text = readTextOperators(decoded.toString(Charsets.ISO_8859_1))
            if (text.isNotBlank()) {
                parts += text
                collected += text.length
            }
            cursor = end + "endstream".length
        }
        return parts.joinToString("\n")
            .replace(Regex("[ \\t\\u0000]+"), " ")
            .trim()
            .take(maxChars)
    }

    private fun nextStream(latin: String, from: Int): Int? {
        var index = from
        while (index < latin.length) {
            val found = latin.indexOf("stream", index)
            if (found < 0) return null
            val precededByEnd = found >= 3 && latin.regionMatches(found - 3, "end", 0, 3)
            val boundaryBefore = found == 0 || !latin[found - 1].isLetter()
            if (!precededByEnd && boundaryBefore) return found
            index = found + 6
        }
        return null
    }

    private fun skipLineBreak(text: String, index: Int): Int {
        var cursor = index
        if (cursor < text.length && text[cursor] == '\r') cursor++
        if (cursor < text.length && text[cursor] == '\n') cursor++
        return cursor
    }

    private fun trimLineBreakBefore(text: String, start: Int, end: Int): Int {
        var cursor = end
        if (cursor > start && text[cursor - 1] == '\n') cursor--
        if (cursor > start && text[cursor - 1] == '\r') cursor--
        return cursor
    }

    private fun inflate(raw: ByteArray): ByteArray? {
        if (raw.isEmpty()) return null
        return inflateWith(raw, nowrap = false) ?: inflateWith(raw, nowrap = true)
    }

    private fun inflateWith(raw: ByteArray, nowrap: Boolean): ByteArray? {
        val inflater = Inflater(nowrap)
        return try {
            inflater.setInput(raw)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var total = 0
            while (!inflater.finished() && total < MAX_INFLATED_BYTES) {
                val count = inflater.inflate(buffer)
                if (count <= 0) break
                output.write(buffer, 0, count)
                total += count
            }
            if (total == 0) null else output.toByteArray()
        } catch (_: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun readTextOperators(content: String): String {
        val pieces = ArrayList<String>()
        var index = 0
        while (index < content.length) {
            when (content[index]) {
                '(' -> {
                    val parsed = parseLiteral(content, index) ?: break
                    val operator = skipSpace(content, parsed.second)
                    if (startsWithOperator(content, operator, "Tj") ||
                        startsWithOperator(content, operator, "'") ||
                        startsWithOperator(content, operator, "\"")
                    ) {
                        if (parsed.first.isNotBlank()) pieces += parsed.first
                    }
                    index = parsed.second
                }
                '[' -> {
                    val array = parseTextArray(content, index)
                    if (array != null && array.second.isNotBlank()) pieces += array.second
                    index = array?.first ?: index + 1
                }
                '<' -> {
                    if (index + 1 < content.length && content[index + 1] == '<') {
                        val close = content.indexOf(">>", index + 2)
                        index = if (close < 0) content.length else close + 2
                    } else {
                        val parsed = parseHex(content, index)
                        if (parsed == null) {
                            index++
                        } else {
                            val operator = skipSpace(content, parsed.second)
                            if (startsWithOperator(content, operator, "Tj")) {
                                if (parsed.first.isNotBlank()) pieces += parsed.first
                            }
                            index = parsed.second
                        }
                    }
                }
                else -> index++
            }
        }
        return pieces.joinToString(" ")
    }

    private fun parseTextArray(content: String, start: Int): Pair<Int, String>? {
        val pieces = ArrayList<String>()
        var index = start + 1
        while (index < content.length) {
            when (content[index]) {
                ']' -> {
                    val operator = skipSpace(content, index + 1)
                    val text = if (startsWithOperator(content, operator, "TJ")) pieces.joinToString("") else ""
                    return index + 1 to text
                }
                '(' -> {
                    val parsed = parseLiteral(content, index) ?: return null
                    pieces += parsed.first
                    index = parsed.second
                }
                '<' -> {
                    if (index + 1 < content.length && content[index + 1] == '<') {
                        index += 2
                    } else {
                        val parsed = parseHex(content, index)
                        if (parsed == null) {
                            index++
                        } else {
                            pieces += parsed.first
                            index = parsed.second
                        }
                    }
                }
                else -> index++
            }
        }
        return null
    }

    private fun parseLiteral(text: String, start: Int): Pair<String, Int>? {
        if (start >= text.length || text[start] != '(') return null
        val out = StringBuilder()
        var index = start + 1
        var depth = 1
        while (index < text.length && depth > 0) {
            val character = text[index]
            if (character == '\\') {
                if (index + 1 >= text.length) break
                when (val escaped = text[index + 1]) {
                    'n' -> {
                        out.append('\n')
                        index += 2
                    }
                    'r' -> {
                        out.append('\r')
                        index += 2
                    }
                    't' -> {
                        out.append('\t')
                        index += 2
                    }
                    'b', 'f' -> index += 2
                    '(', ')', '\\' -> {
                        out.append(escaped)
                        index += 2
                    }
                    '\n' -> index += 2
                    '\r' -> {
                        index += 2
                        if (index < text.length && text[index] == '\n') index++
                    }
                    else -> {
                        if (escaped in '0'..'7') {
                            val octal = StringBuilder()
                            var cursor = index + 1
                            while (cursor < text.length && cursor < index + 4 && text[cursor] in '0'..'7') {
                                octal.append(text[cursor])
                                cursor++
                            }
                            out.append(octal.toString().toInt(8).toChar())
                            index = cursor
                        } else {
                            out.append(escaped)
                            index += 2
                        }
                    }
                }
            } else if (character == '(') {
                depth++
                out.append(character)
                index++
            } else if (character == ')') {
                depth--
                if (depth > 0) out.append(character)
                index++
            } else {
                out.append(character)
                index++
            }
        }
        return out.toString() to index
    }

    private fun parseHex(text: String, start: Int): Pair<String, Int>? {
        if (start >= text.length || text[start] != '<') return null
        val close = text.indexOf('>', start + 1)
        if (close < 0) return null
        val hex = text.substring(start + 1, close).filter { !it.isWhitespace() }
        if (hex.isEmpty() || hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        val padded = if (hex.length % 2 == 0) hex else hex + "0"
        val bytes = ByteArray(padded.length / 2)
        for (index in bytes.indices) {
            bytes[index] = padded.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        return bytes.toString(Charsets.ISO_8859_1) to close + 1
    }

    private fun skipSpace(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isWhitespace()) index++
        return index
    }

    private fun startsWithOperator(text: String, index: Int, operator: String): Boolean {
        if (!text.startsWith(operator, index)) return false
        val end = index + operator.length
        return end >= text.length || !text[end].isLetter()
    }
}
