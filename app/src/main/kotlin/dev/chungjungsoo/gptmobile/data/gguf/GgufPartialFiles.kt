package dev.chungjungsoo.gptmobile.data.gguf

import java.security.MessageDigest

object GgufPartialFiles {
    const val PARTIAL_SUFFIX = ".gguf.partial"
    private val KEY = Regex("[A-Za-z0-9._-]{1,80}")

    fun isSafeKey(key: String): Boolean = KEY.matches(key) && key != "." && key != ".."

    fun keyForUrl(canonicalUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonicalUrl.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { byte -> "%02x".format(byte) }
        return "hf-${hex.take(20)}"
    }
}
