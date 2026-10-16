package dev.chungjungsoo.gptmobile.data.gguf

import java.net.URI

data class HuggingFaceGgufLink(
    val repoId: String,
    val revision: String,
    val filePath: String,
    val downloadUrl: String,
    val pointerUrl: String
) {
    val fileName: String = filePath.substringAfterLast('/')
}

object HuggingFaceGgufUrl {
    fun parse(raw: String): HuggingFaceGgufLink? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (!uri.userInfo.isNullOrBlank()) return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "huggingface.co" && host != "www.huggingface.co") return null
        val segments = uri.path.split('/').filter { it.isNotEmpty() }
        if (segments.size < 5) return null
        val owner = segments[0]
        val repo = segments[1]
        val kind = segments[2]
        val revision = segments[3]
        if (kind != "resolve" && kind != "blob") return null
        if (!isSafeSegment(owner) || !isSafeSegment(repo) || !isSafeSegment(revision)) return null
        val fileSegments = segments.drop(4)
        if (fileSegments.any { !isSafeSegment(it) }) return null
        val filePath = fileSegments.joinToString("/")
        if (!filePath.endsWith(".gguf", ignoreCase = true)) return null
        val repoId = "$owner/$repo"
        return HuggingFaceGgufLink(
            repoId = repoId,
            revision = revision,
            filePath = filePath,
            downloadUrl = "https://huggingface.co/$repoId/resolve/$revision/$filePath",
            pointerUrl = "https://huggingface.co/$repoId/raw/$revision/$filePath"
        )
    }

    private fun isSafeSegment(segment: String): Boolean = segment.isNotEmpty() &&
        segment != "." &&
        segment != ".." &&
        !segment.contains('\\')
}
