package dev.chungjungsoo.gptmobile.data.canvas

import dev.chungjungsoo.gptmobile.data.instruction.ChatCompletionContext

private val CANVAS_WORD = Regex("\\bcanvas\\b", RegexOption.IGNORE_CASE)

fun userAskedForCanvas(text: String): Boolean = CANVAS_WORD.containsMatchIn(text)

fun canvasInstructions(
    document: CanvasDocument?,
    selection: PendingSelection?,
    userAskedToOpen: Boolean
): String? {
    if (document == null) {
        return if (userAskedToOpen) OPEN_INSTRUCTIONS else null
    }
    return buildString {
        append(EDIT_INSTRUCTIONS)
        append("\n\nid: ")
        append(document.id)
        append("\ntitle: ")
        append(document.title)
        append("\nkind: ")
        append(if (document.kind == CanvasKind.Markdown) "markdown" else "code")
        append("\nlanguage: ")
        append(document.language)
        selection?.takeIf { it.text.isNotBlank() }?.let { pending ->
            append("\n\nThe user selected this passage. Change only that passage with action propose-selection and put only the replacement text after ---.\n")
            append(pending.text)
        }
        append("\n\n<<<CANVAS\n")
        append(excerpt(document.content))
        append("\nCANVAS>>>")
    }
}

fun mergeCanvasInstructions(base: ChatCompletionContext, extra: String?): ChatCompletionContext {
    val addition = extra?.takeIf { it.isNotBlank() } ?: return base
    val chat = listOfNotNull(base.chatInstructions?.takeIf { it.isNotBlank() }, addition).joinToString("\n\n")
    return base.copy(chatInstructions = chat)
}

private fun excerpt(content: String): String {
    if (content.length <= CanvasLimits.PROMPT_EXCERPT_CHARS) return content
    val head = content.take(8_000)
    val tail = content.takeLast(2_000)
    return "$head\n\n[truncated]\n\n$tail"
}

private val OPEN_INSTRUCTIONS = """
You can open a canvas document for the user. Reply with a short note and one fence:

```robin-canvas
title: A short title
kind: markdown
language: markdown
action: open
---
document body
```

Use kind code and a language such as kotlin when the document is source code. Do not preview HTML or SVG in the canvas.
""".trimIndent()

private val EDIT_INSTRUCTIONS = """
You are editing the open canvas. Reply with a short note and one robin-canvas fence. Do not paste the document outside the fence.
Use action propose and the full document after --- when the whole document changes.
Use action propose-selection and only the replacement text after --- when a passage is selected.
Keep the same id. To start a different document, use action open and omit id.
""".trimIndent()
