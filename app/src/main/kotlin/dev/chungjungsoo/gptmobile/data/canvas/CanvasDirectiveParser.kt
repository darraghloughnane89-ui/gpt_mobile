package dev.chungjungsoo.gptmobile.data.canvas

private val CANVAS_FENCE = Regex("(?ms)^[ \\t]{0,3}```robin-canvas[ \\t]*\\n(.*?)\\n?[ \\t]{0,3}```")
private val HEADER = Regex("^([A-Za-z][A-Za-z0-9_-]*)\\s*:\\s*(.*)$")

fun extractCanvasDirectives(markdown: String): List<CanvasDirective> = CANVAS_FENCE.findAll(markdown).map { match ->
    parseCanvasDirective(match.groupValues[1], fingerprintFor(markdown, match.range.first))
}.toList()

fun stripCanvasFences(markdown: String): String {
    val stripped = CANVAS_FENCE.replace(markdown, "\n")
    return stripped.replace(Regex("\n{3,}"), "\n\n").trim()
}

fun canvasDisplayText(markdown: String): String {
    val stripped = stripCanvasFences(markdown)
    if (stripped.isNotBlank()) return stripped
    val titles = extractCanvasDirectives(markdown).mapNotNull { directive ->
        directive.title?.trim()?.takeIf { it.isNotEmpty() }
    }
    return titles.joinToString().ifBlank { stripped }
}

fun parseCanvasDirective(fenceBody: String, fingerprint: String): CanvasDirective {
    val lines = fenceBody.split('\n')
    val headerLines = mutableListOf<String>()
    var index = 0
    while (index < lines.size && HEADER.matches(lines[index].trim())) {
        headerLines += lines[index].trim()
        index++
    }
    val headers = if (index < lines.size && lines[index].trim() == "---") {
        index++
        headerLines.mapNotNull { line ->
            HEADER.matchEntire(line)?.let { match ->
                match.groupValues[1].lowercase() to match.groupValues[2].trim()
            }
        }.toMap()
    } else {
        index = 0
        emptyMap()
    }
    val body = lines.drop(index).joinToString("\n").removeSuffix("\n")
    val id = headers["id"]?.takeIf { it.isNotBlank() }
    return CanvasDirective(
        fingerprint = fingerprint,
        id = id,
        title = headers["title"]?.takeIf { it.isNotBlank() },
        kind = parseCanvasKind(headers["kind"]),
        language = headers["language"]?.takeIf { it.isNotBlank() },
        action = parseCanvasAction(headers["action"], hasId = id != null),
        body = body
    )
}

fun parseCanvasKind(value: String?): CanvasKind? = when (value?.trim()?.lowercase()) {
    "markdown", "md" -> CanvasKind.Markdown
    "code" -> CanvasKind.Code
    else -> null
}

fun parseCanvasAction(value: String?, hasId: Boolean): CanvasAction = when (value?.trim()?.lowercase()) {
    "open" -> CanvasAction.Open
    "propose", "replace", "update" -> CanvasAction.Propose
    "propose-selection", "propose_selection", "selection" -> CanvasAction.ProposeSelection
    else -> if (hasId) CanvasAction.Propose else CanvasAction.Open
}

fun cleanCanvasTitle(title: String?, fallback: String = "Untitled canvas"): String {
    val cleaned = title?.trim().orEmpty().replace('\n', ' ').take(CanvasLimits.MAX_TITLE_CHARS)
    return cleaned.ifBlank { fallback }
}

private fun fingerprintFor(message: String, offset: Int): String = "${message.length}:$offset:${message.hashCode()}"
