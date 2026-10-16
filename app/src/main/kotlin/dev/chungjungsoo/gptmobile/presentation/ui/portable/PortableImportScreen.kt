package dev.chungjungsoo.gptmobile.presentation.ui.portable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.BuiltInAgentTool
import dev.chungjungsoo.gptmobile.data.portable.PortableFailure
import dev.chungjungsoo.gptmobile.data.portable.PortableKind
import dev.chungjungsoo.gptmobile.data.portable.PortablePolicy
import dev.chungjungsoo.gptmobile.data.portable.PortablePreview
import dev.chungjungsoo.gptmobile.data.portable.PortableToolDecision
import dev.chungjungsoo.gptmobile.data.portable.PortableToolRequest
import dev.chungjungsoo.gptmobile.data.portable.PortableWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortableImportScreen(
    onBack: () -> Unit,
    onImported: (PortableKind) -> Unit,
    viewModel: PortableImportViewModel = hiltViewModel()
) {
    val preview = viewModel.preview.collectAsStateWithLifecycle().value
    val failure = viewModel.failure.collectAsStateWithLifecycle().value
    val saving = viewModel.saving.collectAsStateWithLifecycle().value
    val finished = viewModel.finished.collectAsStateWithLifecycle().value

    LaunchedEffect(finished) {
        if (finished != null) onImported(finished)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.portable_import_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.dismiss()
                            onBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.go_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.portable_untrusted_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            when {
                preview != null -> PreviewBody(
                    preview = preview,
                    saving = saving,
                    onConfirm = viewModel::confirm,
                    onCancel = {
                        viewModel.dismiss()
                        onBack()
                    }
                )
                failure != null -> {
                    Text(
                        text = stringResource(failureMessage(failure)),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                else -> Text(stringResource(R.string.portable_invalid))
            }
        }
    }
}

@Composable
private fun PreviewBody(
    preview: PortablePreview,
    saving: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Text(
        text = stringResource(kindLabel(preview.kind)),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary
    )
    Text(text = preview.title, style = MaterialTheme.typography.headlineSmall)
    if (preview.description.isNotBlank()) {
        Text(text = preview.description, style = MaterialTheme.typography.bodyLarge)
    }
    preview.modelHint?.let { model ->
        Text(
            text = stringResource(R.string.portable_model_hint, model),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    preview.warnings.sortedBy { it.name }.forEach { warning ->
        Text(
            text = stringResource(warningMessage(warning)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(text = stringResource(R.string.portable_instructions_heading), style = MaterialTheme.typography.titleSmall)
    Text(
        text = preview.instructions.ifBlank { stringResource(R.string.bot_no_instructions) },
        style = MaterialTheme.typography.bodyMedium
    )
    if (preview.lines.isNotEmpty()) {
        Text(
            text = stringResource(linesHeading(preview.kind)),
            style = MaterialTheme.typography.titleSmall
        )
        preview.lines.forEach { line ->
            Text(text = line, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(text = stringResource(R.string.portable_tools_heading), style = MaterialTheme.typography.titleSmall)
    if (preview.tools.isEmpty()) {
        Text(text = stringResource(R.string.portable_no_tools), style = MaterialTheme.typography.bodyMedium)
    } else {
        preview.tools.forEach { tool ->
            Text(text = toolLine(tool), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Button(
        onClick = onConfirm,
        enabled = !saving,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.portable_import_action))
    }
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.cancel))
    }
}

@Composable
private fun toolLine(tool: PortableToolRequest): String {
    val name = when (tool.name) {
        BuiltInAgentTool.CURRENT_DATE -> stringResource(R.string.current_date)
        BuiltInAgentTool.READ_URL -> stringResource(R.string.read_url)
        PortablePolicy.WEB_SEARCH -> stringResource(R.string.web_search)
        PortablePolicy.MCP -> stringResource(R.string.mcp_tools)
        else -> tool.name
    }
    val decision = when (tool.decision) {
        PortableToolDecision.ENABLE -> if (tool.name == BuiltInAgentTool.READ_URL) {
            stringResource(R.string.portable_tool_read_url)
        } else {
            stringResource(R.string.portable_tool_enabled)
        }
        PortableToolDecision.RECORD_ONLY -> stringResource(R.string.portable_tool_recorded)
        PortableToolDecision.WITHHOLD -> stringResource(R.string.portable_tool_withheld)
    }
    return stringResource(R.string.portable_tool_line, name, decision)
}

private fun kindLabel(kind: PortableKind): Int = when (kind) {
    PortableKind.BOT -> R.string.bot
    PortableKind.SKILL -> R.string.portable_skill
    PortableKind.PROMPT -> R.string.portable_prompt
}

private fun linesHeading(kind: PortableKind): Int = when (kind) {
    PortableKind.BOT -> R.string.starter_prompts
    else -> R.string.portable_examples
}

private fun warningMessage(warning: PortableWarning): Int = when (warning) {
    PortableWarning.STRIPPED_SECRETS -> R.string.portable_stripped
    PortableWarning.IGNORED_SIGNATURE -> R.string.portable_signature_ignored
    PortableWarning.IGNORED_IDENTITY -> R.string.portable_identity_ignored
    PortableWarning.SLUG_ADJUSTED -> R.string.portable_slug_adjusted
}

fun failureMessage(failure: PortableFailure): Int = when (failure) {
    PortableFailure.TOO_LARGE -> R.string.portable_too_large
    PortableFailure.LINK_TOO_LARGE -> R.string.portable_link_too_large
    PortableFailure.NOT_A_DOCUMENT -> R.string.portable_invalid
    PortableFailure.UNSUPPORTED_VERSION -> R.string.portable_unsupported
    PortableFailure.KIND_MISMATCH -> R.string.portable_kind_mismatch
    PortableFailure.INVALID_ITEM -> R.string.portable_invalid_item
    PortableFailure.LIBRARY_FULL -> R.string.portable_library_full
}
