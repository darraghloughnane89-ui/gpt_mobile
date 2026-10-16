package dev.chungjungsoo.gptmobile.data.portable

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Deep links carry gzip-compressed JSON, then base64url without padding.
 * The encoded query is capped so a `robin://` link cannot smuggle a large file.
 * Decompression stops at [PortableLimits.MAX_DOCUMENT_BYTES].
 */
object PortablePayload {
    sealed interface Decoded {
        data class Bytes(val value: ByteArray) : Decoded
        data object TooLarge : Decoded
        data object Invalid : Decoded
    }

    fun compress(json: String): String {
        val raw = json.toByteArray(Charsets.UTF_8)
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip -> gzip.write(raw) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray())
    }

    fun decompress(payload: String): Decoded {
        if (payload.length > PortableLimits.MAX_LINK_PAYLOAD_CHARS) return Decoded.TooLarge
        val compressed = try {
            Base64.getUrlDecoder().decode(pad(payload))
        } catch (_: IllegalArgumentException) {
            return Decoded.Invalid
        }
        if (compressed.size > PortableLimits.MAX_LINK_PAYLOAD_CHARS) return Decoded.TooLarge
        return try {
            GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
                val bytes = readCapped(gzip, PortableLimits.MAX_DOCUMENT_BYTES)
                if (bytes == null) Decoded.TooLarge else Decoded.Bytes(bytes)
            }
        } catch (_: Exception) {
            Decoded.Invalid
        }
    }

    fun readCapped(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > max) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun pad(payload: String): String {
        val remainder = payload.length % 4
        if (remainder == 0) return payload
        return payload + "=".repeat(4 - remainder)
    }
}
