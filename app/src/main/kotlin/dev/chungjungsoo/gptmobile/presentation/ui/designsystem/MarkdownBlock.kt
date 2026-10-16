package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Code(val language: String?, val code: String) : MarkdownBlock
}

fun parseSimpleMarkdown(source: String): List<MarkdownBlock> {
    if (source.isEmpty()) return emptyList()
    val blocks = mutableListOf<MarkdownBlock>()
    val fence = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```")
    var cursor = 0
    fence.findAll(source).forEach { match ->
        val before = source.substring(cursor, match.range.first).trim()
        if (before.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(before)
        }
        val language = match.groupValues[1].trim().ifEmpty { null }
        blocks += MarkdownBlock.Code(language, match.groupValues[2].trimEnd())
        cursor = match.range.last + 1
    }
    val tail = source.substring(cursor).trim()
    if (tail.isNotEmpty()) {
        blocks += MarkdownBlock.Paragraph(tail)
    }
    return blocks
}
