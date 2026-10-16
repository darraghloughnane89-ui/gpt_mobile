package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.agent.tool.code.toCodeExecutionView
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEvent
import dev.chungjungsoo.gptmobile.data.database.entity.ToolEventStatus
import dev.chungjungsoo.gptmobile.presentation.theme.defaultSpatialSpec
import dev.chungjungsoo.gptmobile.presentation.theme.fastEffectsSpec

@Composable
fun CodeExecutionBlock(
    event: ToolEvent,
    modifier: Modifier = Modifier,
    contentIdentity: Any = event.eventId,
    onViewFull: (String) -> Unit = {}
) {
    val view = event.toCodeExecutionView()
    var expanded by rememberSaveable(contentIdentity) { mutableStateOf(false) }
    var previewOpen by rememberSaveable(contentIdentity) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = fastEffectsSpec(),
        label = "code execution rotation"
    )
    val status = codeStatusLabel(event)
    val summary = stringResource(R.string.code_execution_summary, status)
    val expandLabel = stringResource(R.string.tool_trace_expand_content_description)
    val collapseLabel = stringResource(R.string.tool_trace_collapse_content_description)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .semantics { contentDescription = summary }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .semantics {
                    role = Role.Button
                    contentDescription = if (expanded) collapseLabel else expandLabel
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = defaultSpatialSpec()) + fadeIn(animationSpec = fastEffectsSpec()),
            exit = shrinkVertically(animationSpec = defaultSpatialSpec()) + fadeOut(animationSpec = fastEffectsSpec())
        ) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                CodeBlock(stringResource(R.string.code_execution_code), view.code, onViewFull)
                if (view.text.isNotBlank()) {
                    CodeBlock(stringResource(R.string.code_execution_result), view.text, onViewFull)
                }
                view.chart?.let { chart ->
                    ChartSpecCanvas(
                        spec = chart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
                if (view.svg != null) {
                    val previewDescription = stringResource(R.string.code_execution_preview_title)
                    SvgArtifactImage(
                        markup = view.svg,
                        contentDescription = previewDescription,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .padding(top = 8.dp)
                    )
                    TextButton(onClick = { previewOpen = true }) {
                        Text(stringResource(R.string.code_execution_open_preview))
                    }
                } else if (view.svgRejected) {
                    Text(
                        text = stringResource(R.string.code_execution_preview_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.code_execution_untrusted),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    if (previewOpen && view.svg != null) {
        val svg = view.svg
        Dialog(onDismissRequest = { previewOpen = false }) {
            Surface(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.code_execution_preview_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.code_execution_untrusted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    SvgArtifactImage(
                        markup = svg,
                        contentDescription = stringResource(R.string.code_execution_preview_title),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                    TextButton(onClick = { previewOpen = false }) {
                        Text(stringResource(R.string.code_execution_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(
    label: String,
    value: String,
    onViewFull: (String) -> Unit
) {
    var overflow by rememberSaveable(value) { mutableStateOf(false) }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
    SelectionContainer {
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 12,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { overflow = it.hasVisualOverflow }
        )
    }
    if (overflow || value.length > 1024 || value.count { it == '\n' } >= 12) {
        TextButton(onClick = { onViewFull(value) }) {
            Text(stringResource(R.string.view_full))
        }
    }
}

@Composable
private fun codeStatusLabel(event: ToolEvent): String = when {
    event.status == ToolEventStatus.RUNNING || event.status == ToolEventStatus.PENDING ->
        stringResource(R.string.tool_trace_status_running)

    event.status == ToolEventStatus.CANCELED -> stringResource(R.string.tool_trace_status_canceled)

    event.status == ToolEventStatus.FAILED || event.isError -> stringResource(R.string.tool_trace_status_failed)

    else -> stringResource(R.string.tool_trace_status_completed)
}
