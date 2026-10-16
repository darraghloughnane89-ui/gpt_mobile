package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.caverock.androidsvg.SVG
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.agent.tool.code.SvgArtifact

@Composable
internal fun SvgArtifactImage(
    markup: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val sanitized = remember(markup) { SvgArtifact.sanitize(markup) }
    val bitmap = remember(sanitized) { sanitized?.let { runCatching { renderSvg(it) }.getOrNull() } }
    if (bitmap == null) {
        Text(
            text = stringResource(R.string.code_execution_preview_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}

private fun renderSvg(markup: String): Bitmap {
    SVG.setInternalEntitiesEnabled(false)
    val svg = SVG.getFromString(markup)
    val width = 480
    val height = 270
    svg.setDocumentWidth(width.toFloat())
    svg.setDocumentHeight(height.toFloat())
    val picture = svg.renderToPicture(width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawPicture(picture)
    return bitmap
}
