package dev.chungjungsoo.gptmobile.data.canvas

/**
 * Read-only HTML and SVG previews belong to artifact preview
 * (`ChatArtifact` / `ArtifactPreviewDialog` on
 * `cursor/chat-library-artifacts-467f`, PR #14). Canvas edits Markdown and code.
 * The fence pattern matches that preview so a Markdown artifact can be handed
 * here with [canvasSeedFromPreviewArtifact] instead of rendering it again.
 */
private val FENCE = Regex("(?ms)^[ \\t]{0,3}```([^\\n`]*)\\n(.*?)\\n?[ \\t]{0,3}```")

fun canvasSeedFromPreviewArtifact(
    title: String,
    language: String,
    source: String,
    kindName: String
): CanvasSeed? {
    if (source.isBlank() || source.length > CanvasLimits.MAX_CONTENT_CHARS) return null
    if (isPreviewOwnedKind(kindName) || isPreviewOwnedFence(language, source)) return null
    val kind = when (kindName.trim().lowercase()) {
        "markdown", "md" -> CanvasKind.Markdown
        "code" -> CanvasKind.Code
        else -> return null
    }
    val resolvedLanguage = when (kind) {
        CanvasKind.Markdown -> "markdown"
        CanvasKind.Code -> language.ifBlank { "text" }
    }
    return CanvasSeed(
        title = cleanCanvasTitle(title, fallback = if (kind == CanvasKind.Markdown) "Markdown" else language),
        kind = kind,
        language = resolvedLanguage,
        content = source.trim()
    )
}

fun editableCanvasSeeds(markdown: String): List<CanvasSeed> {
    val seeds = mutableListOf<CanvasSeed>()
    FENCE.findAll(markdown).forEach { match ->
        if (seeds.size >= 8) return@forEach
        val language = match.groupValues[1].trim().substringBefore(' ').substringBefore('\t').lowercase()
        val body = match.groupValues[2].trim()
        if (language == "robin-canvas") return@forEach
        if (body.isEmpty() || body.length > CanvasLimits.MAX_CONTENT_CHARS) return@forEach
        if (isPreviewOwnedFence(language, body)) return@forEach
        when (language) {
            "markdown", "md" -> seeds += CanvasSeed(
                title = "Markdown",
                kind = CanvasKind.Markdown,
                language = "markdown",
                content = body
            )

            "" -> Unit

            else -> seeds += CanvasSeed(
                title = language,
                kind = CanvasKind.Code,
                language = language,
                content = body
            )
        }
    }
    return titleSeeds(seeds)
}

fun planAssistantOpen(content: String): AssistantCanvasPlan {
    val directives = extractCanvasDirectives(content)
    if (directives.isNotEmpty()) return AssistantCanvasPlan.Directives(directives)
    val seeds = editableCanvasSeeds(content)
    if (seeds.isNotEmpty()) return AssistantCanvasPlan.Seed(seeds.first())
    if (containsPreviewOwnedFence(content) && stripPreviewFences(content).isBlank()) {
        return AssistantCanvasPlan.PreviewOwned
    }
    val prose = stripCanvasFences(content).ifBlank { content.trim() }
    if (prose.isBlank()) return AssistantCanvasPlan.Empty
    return AssistantCanvasPlan.WholeMarkdown(prose.take(CanvasLimits.MAX_CONTENT_CHARS))
}

fun containsPreviewOwnedFence(markdown: String): Boolean = FENCE.findAll(markdown).any { match ->
    val language = match.groupValues[1].trim().substringBefore(' ').substringBefore('\t').lowercase()
    isPreviewOwnedFence(language, match.groupValues[2])
}

internal fun isPreviewOwnedKind(kindName: String): Boolean = when (kindName.trim().lowercase()) {
    "html", "htm", "svg" -> true
    else -> false
}

internal fun isPreviewOwnedFence(language: String, body: String): Boolean = when (language) {
    "html", "htm", "svg" -> true
    "xml" -> body.trimStart().startsWith("<svg", ignoreCase = true)
    else -> false
}

private fun stripPreviewFences(markdown: String): String {
    val withoutPreview = FENCE.replace(markdown) { match ->
        val language = match.groupValues[1].trim().substringBefore(' ').substringBefore('\t').lowercase()
        if (isPreviewOwnedFence(language, match.groupValues[2])) "\n" else match.value
    }
    return stripCanvasFences(withoutPreview).trim()
}

private fun titleSeeds(seeds: List<CanvasSeed>): List<CanvasSeed> {
    val counts = seeds.groupingBy { it.language }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return seeds.map { seed ->
        val index = seen.getOrDefault(seed.language, 0) + 1
        seen[seed.language] = index
        if (counts.getValue(seed.language) == 1) {
            seed
        } else {
            seed.copy(title = "${seed.title} $index")
        }
    }
}
