package dev.chungjungsoo.gptmobile.data.gguf

import java.io.File
import java.security.MessageDigest

object GgufSha256 {
    fun hexOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun matches(file: File, expected: String): Boolean {
        val normalized = expected.trim().lowercase()
        if (normalized.length != 64 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' }) return false
        return hexOf(file) == normalized
    }
}
