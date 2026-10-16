package dev.chungjungsoo.gptmobile.presentation.ui.setting

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.diagnostics.StoredCrash
import dev.chungjungsoo.gptmobile.data.diagnostics.StoredProviderError
import dev.chungjungsoo.gptmobile.presentation.common.SettingsSection
import dev.chungjungsoo.gptmobile.presentation.ui.chat.providerFailureTitle
import dev.chungjungsoo.gptmobile.util.pinnedExitUntilCollapsedScrollBehavior
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onNavigationClick: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DiagnosticsTopBar(
                scrollBehavior = scrollBehavior,
                navigationOnClick = onNavigationClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = stringResource(R.string.diagnostics_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Button(
                onClick = {
                    shareRedactedReport(context, viewModel.redactedShareReport())
                },
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.diagnostics_share))
            }
            SettingsSection(title = stringResource(R.string.diagnostics_crashes)) {
                if (uiState.crashes.isEmpty()) {
                    DiagnosticEmptyText(stringResource(R.string.diagnostics_empty_crashes))
                } else {
                    uiState.crashes.forEach { crash ->
                        CrashRow(crash)
                    }
                }
            }
            SettingsSection(title = stringResource(R.string.diagnostics_provider_errors)) {
                if (uiState.providerErrors.isEmpty()) {
                    DiagnosticEmptyText(stringResource(R.string.diagnostics_empty_provider_errors))
                } else {
                    uiState.providerErrors.forEach { error ->
                        ProviderErrorRow(error)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticEmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
    )
}

@Composable
private fun CrashRow(crash: StoredCrash) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
        Text(
            text = formatDiagnosticTime(crash.timeMs),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = crash.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProviderErrorRow(error: StoredProviderError) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
        Text(
            text = "${formatDiagnosticTime(error.timeMs)} · ${providerFailureTitle(error.kind)}",
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = listOf(error.platform, error.model).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (error.detail.isNotBlank()) {
            Text(
                text = error.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    navigationOnClick: () -> Unit
) {
    LargeTopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        ),
        title = {
            Text(
                modifier = Modifier.padding(4.dp),
                text = stringResource(R.string.diagnostics),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(4.dp),
                onClick = navigationOnClick
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.go_back)
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

private fun shareRedactedReport(context: Context, report: String) {
    try {
        val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val file = File(directory, "robin-diagnostics-report.txt")
        file.writeText(report)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diagnostics_share_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("diagnostics", uri)
        }
        val chooser = Intent.createChooser(
            shareIntent,
            context.getString(R.string.diagnostics_share_chooser)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY).forEach { info ->
            context.grantUriPermission(
                info.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            context.getString(R.string.diagnostics_share_failed),
            Toast.LENGTH_SHORT
        ).show()
    }
}

private val diagnosticTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())

private fun formatDiagnosticTime(timeMs: Long): String = diagnosticTimeFormatter.format(Instant.ofEpochMilli(timeMs))
