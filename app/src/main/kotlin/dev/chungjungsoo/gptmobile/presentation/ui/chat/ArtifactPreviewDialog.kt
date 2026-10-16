package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import dev.chungjungsoo.gptmobile.R

@Composable
fun ArtifactPreviewDialog(
    artifacts: List<ChatArtifact>,
    onDismiss: () -> Unit
) {
    if (artifacts.isEmpty()) return
    var selected by remember(artifacts) { mutableIntStateOf(0) }
    val artifact = artifacts[selected.coerceIn(artifacts.indices)]

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(artifact.title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (artifacts.size > 1) {
                    RowOfArtifactTitles(
                        artifacts = artifacts,
                        onSelect = { selected = it }
                    )
                }
                Text(
                    text = stringResource(R.string.artifact_static_note),
                    style = MaterialTheme.typography.bodySmall
                )
                when (artifact.kind) {
                    ArtifactKind.Markdown -> ChatMarkdown(
                        content = artifact.source,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    )

                    ArtifactKind.Html, ArtifactKind.Svg -> ArtifactWebPreview(
                        document = artifactPreviewDocument(artifact),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp, max = 420.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun RowOfArtifactTitles(
    artifacts: List<ChatArtifact>,
    onSelect: (Int) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        artifacts.forEachIndexed { index, artifact ->
            TextButton(onClick = { onSelect(index) }) {
                Text(artifact.title)
            }
        }
    }
}

@Composable
private fun ArtifactWebPreview(
    document: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("about:blank", document, "text/html", "utf-8", null)
        }
    )
}
