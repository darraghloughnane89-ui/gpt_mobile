package dev.chungjungsoo.gptmobile.presentation.ui.chat

private const val MAX_ARTIFACTS = 8
private const val MAX_ARTIFACT_CHARS = 200_000

private val FENCE = Regex("(?ms)^[ \\t]{0,3}```([^\\n`]*)\\n(.*?)\\n?[ \\t]{0,3}```")
private val SCRIPT_BLOCK = Regex("(?is)<script\\b[^>]*>.*?</script>")
private val SCRIPT_TAG = Regex("(?is)</?script\\b[^>]*>")
private val EVENT_HANDLER = Regex("(?i)\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)")
private val JS_URL = Regex("(?i)javascript\\s*:")
private val FRAMED_CONTENT = Regex("(?is)<(iframe|object|embed)\\b[^>]*>.*?</\\1>|<iframe\\b[^>]*>|<object\\b[^>]*>|<embed\\b[^>]*>")

enum class ArtifactKind {
    Html,
    Svg,
    Markdown
}

data class ChatArtifact(
    val title: String,
    val language: String,
    val source: String,
    val kind: ArtifactKind
)

fun extractChatArtifacts(markdown: String): List<ChatArtifact> {
    val found = mutableListOf<ChatArtifact>()
    FENCE.findAll(markdown).forEach { match ->
        if (found.size >= MAX_ARTIFACTS) return@forEach
        val language = match.groupValues[1].trim().substringBefore(' ').substringBefore('\t').lowercase()
        val body = match.groupValues[2].trim()
        val kind = artifactKind(language, body) ?: return@forEach
        if (body.isEmpty() || body.length > MAX_ARTIFACT_CHARS) return@forEach
        found += ChatArtifact(title = "", language = language, source = body, kind = kind)
    }
    return titleArtifacts(found)
}

fun artifactKind(language: String, body: String): ArtifactKind? = when (language) {
    "html", "htm" -> ArtifactKind.Html
    "svg" -> ArtifactKind.Svg
    "markdown", "md" -> ArtifactKind.Markdown
    "xml" -> if (body.trimStart().startsWith("<svg", ignoreCase = true)) ArtifactKind.Svg else null
    else -> null
}

fun sanitizePreviewMarkup(source: String): String = source
    .replace(SCRIPT_BLOCK, "")
    .replace(SCRIPT_TAG, "")
    .replace(FRAMED_CONTENT, "")
    .replace(EVENT_HANDLER, "")
    .replace(JS_URL, "")

fun artifactPreviewDocument(artifact: ChatArtifact): String {
    val body = sanitizePreviewMarkup(artifact.source)
    val wrapped = when (artifact.kind) {
        ArtifactKind.Svg -> "<div class=\"stage\">$body</div>"
        ArtifactKind.Html -> body
        ArtifactKind.Markdown -> body
    }
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          html, body { margin: 0; padding: 16px; font-family: sans-serif; }
          .stage { display: flex; justify-content: center; }
          img, svg { max-width: 100%; height: auto; }
        </style>
        </head>
        <body>$wrapped</body>
        </html>
    """.trimIndent()
}

private fun titleArtifacts(artifacts: List<ChatArtifact>): List<ChatArtifact> {
    val counts = artifacts.groupingBy { it.kind }.eachCount()
    val seen = mutableMapOf<ArtifactKind, Int>()
    return artifacts.map { artifact ->
        val index = seen.getOrDefault(artifact.kind, 0) + 1
        seen[artifact.kind] = index
        val base = when (artifact.kind) {
            ArtifactKind.Html -> "HTML"
            ArtifactKind.Svg -> "SVG"
            ArtifactKind.Markdown -> "Markdown"
        }
        val title = if (counts.getValue(artifact.kind) == 1) base else "$base $index"
        artifact.copy(title = title)
    }
}
