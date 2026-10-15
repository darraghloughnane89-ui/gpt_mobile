package dev.chungjungsoo.gptmobile.data.phone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Renders the first pages of a scanned PDF into JPEG files the existing
 * image attachment path can send.
 */
class PdfPageImageRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun render(pdfFile: File, maxPages: Int = PdfSharePlanner.MAX_RENDERED_PAGES): List<String> {
        if (maxPages <= 0 || !pdfFile.exists() || pdfFile.length() <= 0L) return emptyList()
        val directory = File(context.filesDir, ATTACHMENTS_DIR).apply { mkdirs() }
        val paths = mutableListOf<String>()
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val pageCount = minOf(renderer.pageCount, maxPages)
                for (index in 0 until pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = MAX_EDGE.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val target = File(directory, "pdf_page_${pdfFile.nameWithoutExtension}_$index.jpg")
                            target.outputStream().use { output ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                            }
                            paths += target.absolutePath
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
        return paths
    }

    private companion object {
        const val ATTACHMENTS_DIR = "attachments"
        const val MAX_EDGE = 1600
        const val JPEG_QUALITY = 85
    }
}
