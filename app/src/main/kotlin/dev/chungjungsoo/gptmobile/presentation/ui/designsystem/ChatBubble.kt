package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class BubbleRole {
    User,
    Assistant
}

@Composable
fun ChatBubble(
    text: String,
    role: BubbleRole,
    modifier: Modifier = Modifier,
    author: String? = null,
    streaming: Boolean = false,
    animated: Boolean = true,
    onCopyCode: (String) -> Unit = {}
) {
    val isUser = role == BubbleRole.User
    Column(
        modifier = modifier,
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!author.isNullOrBlank() && !isUser) {
            Text(
                text = author,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Space.xs, bottom = Space.xs)
            )
        }
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = if (isUser) userBubbleShape() else assistantBubbleShape(),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ) {
            Column(modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
                if (isUser) {
                    Text(text = text, style = MaterialTheme.typography.bodyLarge)
                } else {
                    MarkdownMessage(text = text, onCopyCode = onCopyCode)
                    if (streaming) {
                        TypingIndicator(
                            modifier = Modifier.padding(top = Space.sm),
                            animated = animated
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownMessage(
    text: String,
    modifier: Modifier = Modifier,
    onCopyCode: (String) -> Unit = {}
) {
    Column(modifier = modifier) {
        parseSimpleMarkdown(text).forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge
                )
                is MarkdownBlock.Code -> CodeBlock(
                    code = block.code,
                    language = block.language,
                    onCopy = { onCopyCode(block.code) }
                )
            }
        }
    }
}
