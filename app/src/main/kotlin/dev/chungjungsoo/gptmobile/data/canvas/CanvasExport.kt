package dev.chungjungsoo.gptmobile.data.canvas

object CanvasPdfLayout {
    const val COLUMNS = 85
    const val LINES_PER_PAGE = 50
}

fun exportCanvasText(document: CanvasDocument, format: CanvasExportFormat): String = when (format) {
    CanvasExportFormat.PlainText, CanvasExportFormat.Pdf -> document.content
    CanvasExportFormat.Markdown -> if (document.kind == CanvasKind.Code) {
        "```${document.language}\n${document.content}\n```\n"
    } else {
        document.content
    }
}

fun exportCanvasFileName(title: String, format: CanvasExportFormat): String {
    val slug = title.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "canvas" }
        .take(40)
    val extension = when (format) {
        CanvasExportFormat.Markdown -> "md"
        CanvasExportFormat.PlainText -> "txt"
        CanvasExportFormat.Pdf -> "pdf"
    }
    return "$slug.$extension"
}

fun paginateForPdf(
    text: String,
    columns: Int = CanvasPdfLayout.COLUMNS,
    linesPerPage: Int = CanvasPdfLayout.LINES_PER_PAGE
): List<List<String>> {
    val wrapped = text.replace("\r\n", "\n").split('\n').flatMap { line -> wrapLine(line, columns) }
    val lines = wrapped.ifEmpty { listOf("") }
    return lines.chunked(linesPerPage.coerceAtLeast(1))
}

fun wrapLine(line: String, columns: Int): List<String> {
    if (columns < 1 || line.length <= columns) return listOf(line)
    val parts = mutableListOf<String>()
    var rest = line
    while (rest.length > columns) {
        val breakAt = rest.lastIndexOf(' ', columns).takeIf { it > 0 } ?: columns
        parts += rest.substring(0, breakAt).trimEnd()
        rest = rest.substring(breakAt).trimStart()
    }
    if (rest.isNotEmpty() || parts.isEmpty()) parts += rest
    return parts
}
