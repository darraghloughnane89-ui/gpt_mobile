package dev.chungjungsoo.gptmobile.data.gguf

import java.io.File
import java.io.InputStream

/**
 * Reads the GGUF header defined by llama.cpp (`gguf.h`).
 *
 * SmolChat-Android reads the same `*.context_length` key through a JNI `GGUFReader`
 * linked to the llama.cpp submodule. This reader stays in Kotlin so the app does not
 * have to compile that submodule or ship its extra ABIs.
 */
internal object GgufMetadata {
    private const val MAGIC = 0x46554747L
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT32 = 4
    private const val TYPE_UINT64 = 10
    private const val MAX_KEYS = 4096
    private const val MAX_ARRAY = 1_000_000L
    private const val MAX_STRING = 1_048_576L

    fun isGguf(file: File): Boolean = runCatching {
        file.inputStream().use { input -> readU32(input) == MAGIC }
    }.getOrDefault(false)

    fun contextLength(file: File): Long? = runCatching {
        file.inputStream().use { input -> contextLength(input) }
    }.getOrNull()

    private fun contextLength(input: InputStream): Long? {
        if (readU32(input) != MAGIC) return null
        val version = readU32(input)
        if (version < 2L) return null
        readU64(input)
        val keyCount = readU64(input)
        if (keyCount > MAX_KEYS) return null
        var found: Long? = null
        repeat(keyCount.toInt()) {
            val key = readString(input)
            val type = readU32(input).toInt()
            if (key.endsWith(".context_length") && (type == TYPE_UINT32 || type == TYPE_UINT64)) {
                found = if (type == TYPE_UINT32) readU32(input) else readU64(input)
            } else if (!skipValue(input, type)) {
                return found
            }
        }
        return found
    }

    private fun skipValue(input: InputStream, type: Int): Boolean = when (type) {
        0, 1, 7 -> {
            readExact(input, 1)
            true
        }
        2, 3 -> {
            readExact(input, 2)
            true
        }
        4, 5, 6 -> {
            readExact(input, 4)
            true
        }
        10, 11, 12 -> {
            readExact(input, 8)
            true
        }
        TYPE_STRING -> {
            readString(input)
            true
        }
        TYPE_ARRAY -> {
            val element = readU32(input).toInt()
            val count = readU64(input)
            if (count > MAX_ARRAY) {
                false
            } else {
                var index = 0L
                var ok = true
                while (index < count && ok) {
                    ok = skipValue(input, element)
                    index += 1
                }
                ok
            }
        }
        else -> false
    }

    private fun readString(input: InputStream): String {
        val length = readU64(input)
        if (length > MAX_STRING) error("GGUF string is too long")
        return String(readExact(input, length.toInt()), Charsets.UTF_8)
    }

    private fun readU32(input: InputStream): Long {
        val bytes = readExact(input, 4)
        return (bytes[0].toLong() and 0xff) or
            ((bytes[1].toLong() and 0xff) shl 8) or
            ((bytes[2].toLong() and 0xff) shl 16) or
            ((bytes[3].toLong() and 0xff) shl 24)
    }

    private fun readU64(input: InputStream): Long {
        val bytes = readExact(input, 8)
        var value = 0L
        for (index in 0 until 8) {
            value = value or ((bytes[index].toLong() and 0xff) shl (index * 8))
        }
        return value
    }

    private fun readExact(input: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read < 0) error("truncated GGUF")
            offset += read
        }
        return buffer
    }
}
