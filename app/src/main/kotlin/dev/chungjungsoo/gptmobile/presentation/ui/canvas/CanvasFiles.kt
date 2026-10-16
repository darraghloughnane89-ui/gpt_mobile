package dev.chungjungsoo.gptmobile.presentation.ui.canvas

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider.getUriForFile
import dev.chungjungsoo.gptmobile.data.canvas.CanvasDocument
import dev.chungjungsoo.gptmobile.data.canvas.CanvasExportFormat
import dev.chungjungsoo.gptmobile.data.canvas.CanvasPdfLayout
import dev.chungjungsoo.gptmobile.data.canvas.exportCanvasFileName
import dev.chungjungsoo.gptmobile.data.canvas.exportCanvasText
import dev.chungjungsoo.gptmobile.data.canvas.paginateForPdf
import java.io.File

fun shareCanvasText(context: Context, document: CanvasDocument, chooserTitle: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, document.title)
        putExtra(Intent.EXTRA_TEXT, document.content)
    }
    context.startActivity(Intent.createChooser(shareIntent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun exportCanvasFile(context: Context, document: CanvasDocument, format: CanvasExportFormat, chooserTitle: String) {
    val directory = context.getExternalFilesDir(null) ?: return
    val file = File(directory, exportCanvasFileName(document.title, format))
    when (format) {
        CanvasExportFormat.Pdf -> writeCanvasPdf(file, document.title, document.content)
        CanvasExportFormat.Markdown, CanvasExportFormat.PlainText -> file.writeText(exportCanvasText(document, format))
    }
    val mime = when (format) {
        CanvasExportFormat.Markdown -> "text/markdown"
        CanvasExportFormat.PlainText -> "text/plain"
        CanvasExportFormat.Pdf -> "application/pdf"
    }
    val uri = getUriForFile(context, "${context.packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri(document.title, uri)
    }
    val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY).forEach { info ->
        context.grantUriPermission(info.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(chooser)
}

internal fun writeCanvasPdf(file: File, title: String, content: String) {
    val document = PdfDocument()
    val titlePaint = Paint().apply {
        textSize = 16f
        isAntiAlias = true
    }
    val bodyPaint = Paint().apply {
        textSize = 11f
        isAntiAlias = true
    }
    val pages = paginateForPdf(content)
    pages.forEachIndexed { index, lines ->
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
        var y = TOP_MARGIN
        if (index == 0 && title.isNotBlank()) {
            page.canvas.drawText(title, SIDE_MARGIN, y, titlePaint)
            y += TITLE_GAP
        }
        lines.take(CanvasPdfLayout.LINES_PER_PAGE).forEach { line ->
            page.canvas.drawText(line, SIDE_MARGIN, y, bodyPaint)
            y += LINE_HEIGHT
        }
        document.finishPage(page)
    }
    file.outputStream().use { output -> document.writeTo(output) }
    document.close()
}

private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val SIDE_MARGIN = 40f
private const val TOP_MARGIN = 48f
private const val LINE_HEIGHT = 14f
private const val TITLE_GAP = 28f
