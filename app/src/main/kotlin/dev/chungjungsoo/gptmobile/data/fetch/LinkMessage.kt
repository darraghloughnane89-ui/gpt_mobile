package dev.chungjungsoo.gptmobile.data.fetch

import dev.chungjungsoo.gptmobile.data.search.UntrustedWebContent

data class AttachedLink(
    val source: String,
    val title: String,
    val kind: LinkKind,
    val text: String
)

data class ParsedLinkMessage(
    val question: String,
    val links: List<AttachedLink>
)

/**
 * Stores a read link beside the user's question. The page body uses the shared
 * [UntrustedWebContent] fence, so it cannot become a tool call or a send.
 */
object LinkMessage {
    fun compose(question: String, links: List<ReadLink>): String {
        if (links.isEmpty()) return question.trim()
        val fences = links.joinToString(separator = "\n\n") { link ->
            UntrustedWebContent.fence(link.text, sourceLabel(link))
        }
        val visible = question.trim()
        return if (visible.isEmpty()) fences else "$visible\n\n$fences"
    }

    fun parse(stored: String): ParsedLinkMessage {
        val links = mutableListOf<AttachedLink>()
        val question = StringBuilder()
        var index = 0
        while (index < stored.length) {
            val open = stored.indexOf(UntrustedWebContent.OPEN, index)
            if (open < 0) {
                question.append(stored.substring(index))
                break
            }
            question.append(stored.substring(index, open))
            val close = stored.indexOf(UntrustedWebContent.CLOSE, open + UntrustedWebContent.OPEN.length)
            if (close < 0) {
                question.append(stored.substring(open))
                break
            }
            links += attached(stored.substring(open + UntrustedWebContent.OPEN.length, close))
            index = close + UntrustedWebContent.CLOSE.length
        }
        return ParsedLinkMessage(question.toString().trim(), links)
    }

    fun replaceQuestion(stored: String, question: String): String {
        val parsed = parse(stored)
        if (parsed.links.isEmpty()) return question
        return compose(
            question,
            parsed.links.map { link ->
                ReadLink(
                    url = link.source,
                    finalUrl = link.source,
                    title = link.title,
                    text = link.text,
                    kind = link.kind
                )
            }
        )
    }

    private fun attached(block: String): AttachedLink {
        val lines = block.trim().lines()
        val sourceLine = lines.firstOrNull { it.startsWith("Source: ") }?.removePrefix("Source: ").orEmpty()
        val split = sourceLine.lastIndexOf(" | ")
        val title = if (split >= 0) sourceLine.substring(0, split).trim() else sourceLine.trim()
        val source = if (split >= 0) sourceLine.substring(split + 3).trim() else sourceLine.trim()
        val text = lines
            .filterNot { it.startsWith("Source: ") || it.startsWith("Untrusted web data follows.") }
            .joinToString(separator = "\n")
            .trim()
        val kind = if (LinkUrls.youtubeVideoId(source) != null) LinkKind.YOUTUBE else LinkKind.PAGE
        return AttachedLink(source = source, title = title.ifBlank { source }, kind = kind, text = text)
    }

    private fun sourceLabel(link: ReadLink): String {
        val title = link.title.replace("|", "/").replace("\n", " ").trim()
        val url = link.finalUrl.ifBlank { link.url }
        return if (title.isBlank() || title == url) url else "$title | $url"
    }
}
