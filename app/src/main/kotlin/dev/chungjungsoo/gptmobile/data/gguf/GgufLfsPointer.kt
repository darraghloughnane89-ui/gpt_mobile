package dev.chungjungsoo.gptmobile.data.gguf

data class GgufPublishedMetadata(
    val sizeBytes: Long?,
    val sha256: String?
)

object GgufLfsPointer {
    private val OID = Regex("""oid sha256:([0-9a-fA-F]{64})""")
    private val SIZE = Regex("""(?m)^size (\d+)\s*$""")

    fun parse(text: String): GgufPublishedMetadata? {
        if (!text.contains("git-lfs")) return null
        val hash = OID.find(text)?.groupValues?.get(1)?.lowercase() ?: return null
        val bytes = SIZE.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        if (bytes <= 0L) return null
        return GgufPublishedMetadata(sizeBytes = bytes, sha256 = hash)
    }
}
