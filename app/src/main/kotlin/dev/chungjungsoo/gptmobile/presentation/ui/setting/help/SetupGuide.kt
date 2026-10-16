package dev.chungjungsoo.gptmobile.presentation.ui.setting.help

data class SetupGuide(
    val title: String,
    val intro: List<GuideBlock>,
    val sections: List<GuideSection>
) {
    companion object {
        const val ASSET = "setup.md"

        fun parse(markdown: String): SetupGuide {
            val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').lines()
            var title = ""
            val intro = mutableListOf<GuideBlock>()
            val sections = mutableListOf<GuideSection>()
            var sectionTitle: String? = null
            val sectionBlocks = mutableListOf<GuideBlock>()
            val paragraph = StringBuilder()
            var index = 0

            fun destination(): MutableList<GuideBlock> = if (sectionTitle == null) intro else sectionBlocks

            fun endParagraph() {
                val text = paragraph.toString().trim()
                if (text.isNotEmpty()) {
                    destination().add(GuideBlock.Paragraph(text))
                }
                paragraph.clear()
            }

            fun closeSection() {
                val heading = sectionTitle ?: return
                sections.add(GuideSection(heading, sectionBlocks.toList()))
                sectionBlocks.clear()
            }

            while (index < lines.size) {
                val trimmed = lines[index].trim()
                when {
                    trimmed.startsWith("```") -> {
                        endParagraph()
                        val code = StringBuilder()
                        index++
                        while (index < lines.size && !lines[index].trim().startsWith("```")) {
                            if (code.isNotEmpty()) code.append('\n')
                            code.append(lines[index])
                            index++
                        }
                        destination().add(GuideBlock.Code(code.toString().trimEnd()))
                    }
                    trimmed.startsWith("### ") -> {
                        endParagraph()
                        destination().add(GuideBlock.Subheading(trimmed.removePrefix("### ").trim()))
                    }
                    trimmed.startsWith("## ") -> {
                        endParagraph()
                        closeSection()
                        sectionTitle = trimmed.removePrefix("## ").trim()
                    }
                    trimmed.startsWith("# ") -> {
                        endParagraph()
                        title = trimmed.removePrefix("# ").trim()
                    }
                    NUMBERED_STEP.matches(trimmed) -> {
                        endParagraph()
                        val steps = mutableListOf<GuideStep>()
                        while (index < lines.size && NUMBERED_STEP.matches(lines[index].trim())) {
                            val match = NUMBERED_STEP.matchEntire(lines[index].trim())
                            if (match != null) {
                                steps.add(
                                    GuideStep(
                                        number = match.groupValues[1].toInt(),
                                        text = match.groupValues[2].trim()
                                    )
                                )
                            }
                            index++
                        }
                        destination().add(GuideBlock.Steps(steps))
                        continue
                    }
                    trimmed.startsWith("- ") -> {
                        endParagraph()
                        val items = mutableListOf<String>()
                        while (index < lines.size && lines[index].trim().startsWith("- ")) {
                            items.add(lines[index].trim().removePrefix("- ").trim())
                            index++
                        }
                        destination().add(GuideBlock.Bullets(items))
                        continue
                    }
                    trimmed.isEmpty() -> endParagraph()
                    else -> {
                        if (paragraph.isNotEmpty()) paragraph.append(' ')
                        paragraph.append(trimmed)
                    }
                }
                index++
            }
            endParagraph()
            closeSection()
            return SetupGuide(title = title, intro = intro, sections = sections)
        }

        private val NUMBERED_STEP = Regex("""^(\d+)\.\s+(.*)$""")
    }
}

data class GuideSection(
    val title: String,
    val blocks: List<GuideBlock>
)

data class GuideStep(
    val number: Int,
    val text: String
)

sealed class GuideBlock {
    data class Paragraph(val text: String) : GuideBlock()
    data class Subheading(val text: String) : GuideBlock()
    data class Steps(val items: List<GuideStep>) : GuideBlock()
    data class Bullets(val items: List<String>) : GuideBlock()
    data class Code(val text: String) : GuideBlock()
}

data class GuideSpan(
    val text: String,
    val code: Boolean = false,
    val strong: Boolean = false
)

fun parseGuideInline(source: String): List<GuideSpan> {
    val spans = mutableListOf<GuideSpan>()
    val buffer = StringBuilder()
    var strong = false
    var code = false
    var index = 0

    fun flush() {
        if (buffer.isNotEmpty()) {
            spans.add(GuideSpan(text = buffer.toString(), code = code, strong = strong))
            buffer.clear()
        }
    }

    while (index < source.length) {
        if (!code && source.startsWith("**", index)) {
            flush()
            strong = !strong
            index += 2
            continue
        }
        if (source[index] == '`') {
            flush()
            code = !code
            index++
            continue
        }
        if (!code && source[index] == '[') {
            val labelEnd = source.indexOf(']', index + 1)
            if (labelEnd > index && labelEnd + 1 < source.length && source[labelEnd + 1] == '(') {
                val urlEnd = source.indexOf(')', labelEnd + 2)
                if (urlEnd > labelEnd) {
                    flush()
                    val label = source.substring(index + 1, labelEnd)
                    val url = source.substring(labelEnd + 2, urlEnd)
                    val text = if (label == url) url else "$label ($url)"
                    spans.add(GuideSpan(text))
                    index = urlEnd + 1
                    continue
                }
            }
        }
        buffer.append(source[index])
        index++
    }
    flush()
    return spans
}
