package dev.chungjungsoo.gptmobile.data.project.archive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

class AndroidProjectImageProcessor : ProjectImageProcessor {
    override fun process(bytes: ByteArray, mimeType: String, keepOriginal: Boolean): ProcessedImage {
        if (keepOriginal) return ProcessedImage(bytes, mimeType, downscaled = false)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return ProcessedImage(bytes, mimeType, downscaled = false)
        val edge = maxOf(width, height)
        if (edge <= ProjectArchiveZip.MAX_IMAGE_EDGE) return ProcessedImage(bytes, mimeType, downscaled = false)
        val sample = ImageScale.sampleSize(width, height, ProjectArchiveZip.MAX_IMAGE_EDGE)
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return ProcessedImage(bytes, mimeType, downscaled = false)
        val (targetWidth, targetHeight) = ImageScale.targetSize(
            decoded.width,
            decoded.height,
            ProjectArchiveZip.MAX_IMAGE_EDGE
        )
        val scaled = if (decoded.width == targetWidth && decoded.height == targetHeight) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }
        val png = scaled.hasAlpha() || mimeType.contains("png")
        val output = ByteArrayOutputStream()
        val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        scaled.compress(format, 85, output)
        scaled.recycle()
        return ProcessedImage(
            bytes = output.toByteArray(),
            mimeType = if (png) "image/png" else "image/jpeg",
            downscaled = true
        )
    }
}
