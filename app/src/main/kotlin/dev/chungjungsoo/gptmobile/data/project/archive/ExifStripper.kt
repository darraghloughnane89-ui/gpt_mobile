package dev.chungjungsoo.gptmobile.data.project.archive

import java.io.ByteArrayOutputStream

/**
 * Removes GPS data from JPEG EXIF and PNG eXIf chunks.
 * Other bytes, including the picture itself, stay as they are.
 */
internal object ExifStripper {
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A
    )

    fun strip(bytes: ByteArray): ByteArray = when {
        isJpeg(bytes) -> stripJpeg(bytes)
        isPng(bytes) -> stripPng(bytes)
        else -> bytes
    }

    fun hasJpegGps(bytes: ByteArray): Boolean {
        if (!isJpeg(bytes)) return false
        var index = 2
        while (index + 4 < bytes.size) {
            if ((bytes[index].toInt() and 0xFF) != 0xFF) return false
            while (index < bytes.size && (bytes[index].toInt() and 0xFF) == 0xFF) index++
            if (index >= bytes.size) return false
            val marker = bytes[index].toInt() and 0xFF
            index++
            if (marker == 0xDA || marker == 0xD9) return false
            if (marker == 0x01 || marker in 0xD0..0xD7) continue
            if (index + 2 > bytes.size) return false
            val length = readBe16(bytes, index)
            if (length < 2 || index + length > bytes.size) return false
            if (marker == 0xE1 && segmentHasGps(bytes, index, length)) return true
            index += length
        }
        return false
    }

    private fun stripJpeg(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(bytes.size)
        output.write(bytes, 0, 2)
        var index = 2
        var changed = false
        while (index + 4 < bytes.size) {
            if ((bytes[index].toInt() and 0xFF) != 0xFF) break
            val markerStart = index
            while (index < bytes.size && (bytes[index].toInt() and 0xFF) == 0xFF) index++
            if (index >= bytes.size) break
            val marker = bytes[index].toInt() and 0xFF
            index++
            if (marker == 0xDA || marker == 0xD9) {
                output.write(bytes, markerStart, bytes.size - markerStart)
                return if (changed) output.toByteArray() else bytes
            }
            if (marker == 0x01 || marker in 0xD0..0xD7) {
                output.write(bytes, markerStart, index - markerStart)
                continue
            }
            if (index + 2 > bytes.size) break
            val length = readBe16(bytes, index)
            if (length < 2 || index + length > bytes.size) break
            if (marker == 0xE1) {
                val rewritten = rewriteExif(bytes.copyOfRange(index, index + length))
                if (!rewritten.contentEquals(bytes.copyOfRange(index, index + length))) changed = true
                output.write(0xFF)
                output.write(marker)
                output.write(rewritten)
            } else {
                output.write(bytes, markerStart, index + length - markerStart)
            }
            index += length
        }
        if (index < bytes.size) output.write(bytes, index, bytes.size - index)
        return if (changed) output.toByteArray() else bytes
    }

    private fun rewriteExif(segment: ByteArray): ByteArray {
        if (segment.size < 16) return segment
        val header = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0)
        if (!segment.copyOfRange(2, 8).contentEquals(header)) return segment
        val copy = segment.copyOf()
        if (clearGpsPointer(copy, tiffOffset = 8)) return copy
        return segment
    }

    private fun segmentHasGps(bytes: ByteArray, offset: Int, length: Int): Boolean {
        if (length < 16 || offset + length > bytes.size) return false
        val header = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0, 0)
        if (!bytes.copyOfRange(offset + 2, offset + 8).contentEquals(header)) return false
        return ifdHasTag(bytes, offset + 8, offset + length, 0x8825)
    }

    private fun clearGpsPointer(segment: ByteArray, tiffOffset: Int): Boolean {
        val end = segment.size
        if (tiffOffset + 8 > end) return false
        val little = segment[tiffOffset] == 'I'.code.toByte()
        if (!little && segment[tiffOffset] != 'M'.code.toByte()) return false
        if (readU16(segment, tiffOffset + 2, little) != 42) return false
        var ifd = readU32(segment, tiffOffset + 4, little).toInt()
        var changed = false
        var guard = 0
        while (ifd in 1 until end && guard++ < 4) {
            val start = tiffOffset + ifd
            if (start + 2 > end) break
            val count = readU16(segment, start, little)
            var entry = start + 2
            for (index in 0 until count) {
                if (entry + 12 > end) return changed
                if (readU16(segment, entry, little) == 0x8825) {
                    writeU16(segment, entry, 0xFFFE, little)
                    writeU32(segment, entry + 8, 0L, little)
                    changed = true
                }
                entry += 12
            }
            if (entry + 4 > end) break
            val next = readU32(segment, entry, little).toInt()
            ifd = next
        }
        return changed
    }

    private fun ifdHasTag(bytes: ByteArray, tiffOffset: Int, end: Int, tag: Int): Boolean {
        if (tiffOffset + 8 > end) return false
        val little = bytes[tiffOffset] == 'I'.code.toByte()
        if (!little && bytes[tiffOffset] != 'M'.code.toByte()) return false
        if (readU16(bytes, tiffOffset + 2, little) != 42) return false
        val ifd = readU32(bytes, tiffOffset + 4, little).toInt()
        val start = tiffOffset + ifd
        if (ifd <= 0 || start + 2 > end) return false
        val count = readU16(bytes, start, little)
        var entry = start + 2
        for (index in 0 until count) {
            if (entry + 12 > end) return false
            if (readU16(bytes, entry, little) == tag) return true
            entry += 12
        }
        return false
    }

    private fun stripPng(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream(bytes.size)
        output.write(bytes, 0, 8)
        var index = 8
        var changed = false
        while (index + 12 <= bytes.size) {
            val length = readBe32(bytes, index)
            if (length < 0 || index + 12L + length > bytes.size) break
            val type = String(bytes, index + 4, 4, Charsets.ISO_8859_1)
            val chunkLength = 12 + length
            if (type == "eXIf") {
                changed = true
            } else {
                output.write(bytes, index, chunkLength)
            }
            index += chunkLength
            if (type == "IEND") break
        }
        return if (changed) output.toByteArray() else bytes
    }

    private fun isJpeg(bytes: ByteArray): Boolean =
        bytes.size > 4 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8

    private fun isPng(bytes: ByteArray): Boolean =
        bytes.size > 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)

    private fun readBe16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun readBe32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun readU16(bytes: ByteArray, offset: Int, little: Boolean): Int = if (little) {
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    } else {
        readBe16(bytes, offset)
    }

    private fun readU32(bytes: ByteArray, offset: Int, little: Boolean): Long {
        val b0 = bytes[offset].toLong() and 0xFF
        val b1 = bytes[offset + 1].toLong() and 0xFF
        val b2 = bytes[offset + 2].toLong() and 0xFF
        val b3 = bytes[offset + 3].toLong() and 0xFF
        return if (little) b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24) else (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int, little: Boolean) {
        if (little) {
            bytes[offset] = (value and 0xFF).toByte()
            bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        } else {
            bytes[offset] = ((value shr 8) and 0xFF).toByte()
            bytes[offset + 1] = (value and 0xFF).toByte()
        }
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long, little: Boolean) {
        val intValue = value.toInt()
        if (little) {
            bytes[offset] = (intValue and 0xFF).toByte()
            bytes[offset + 1] = ((intValue shr 8) and 0xFF).toByte()
            bytes[offset + 2] = ((intValue shr 16) and 0xFF).toByte()
            bytes[offset + 3] = ((intValue shr 24) and 0xFF).toByte()
        } else {
            bytes[offset] = ((intValue shr 24) and 0xFF).toByte()
            bytes[offset + 1] = ((intValue shr 16) and 0xFF).toByte()
            bytes[offset + 2] = ((intValue shr 8) and 0xFF).toByte()
            bytes[offset + 3] = (intValue and 0xFF).toByte()
        }
    }
}
