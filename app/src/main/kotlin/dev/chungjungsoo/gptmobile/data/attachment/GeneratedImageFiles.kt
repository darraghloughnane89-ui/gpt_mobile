package dev.chungjungsoo.gptmobile.data.attachment

import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import java.io.File
import java.util.Base64

object GeneratedImageFiles {
    fun write(
        directory: File,
        payload: GeneratedImagePayload,
        nowMillis: Long = System.currentTimeMillis()
    ): ChatAttachment? {
        val bytes = try {
            Base64.getDecoder().decode(payload.base64Data)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (bytes.isEmpty()) return null
        directory.mkdirs()
        val extension = when {
            payload.mimeType.contains("jpeg") || payload.mimeType.contains("jpg") -> "jpg"
            payload.mimeType.contains("webp") -> "webp"
            payload.mimeType.contains("gif") -> "gif"
            else -> "png"
        }
        val file = uniqueFile(directory, "generated_$nowMillis.$extension")
        file.writeBytes(bytes)
        return ChatAttachment(
            localFilePath = file.absolutePath,
            preparedFilePath = file.absolutePath,
            displayName = file.name,
            mimeType = payload.mimeType.ifBlank { "image/$extension" },
            sizeBytes = bytes.size.toLong()
        )
    }

    private fun uniqueFile(directory: File, name: String): File {
        val candidate = File(directory, name)
        if (!candidate.exists()) return candidate
        val stem = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "")
        val unique = if (extension.isEmpty()) {
            "${stem}_${System.nanoTime()}"
        } else {
            "${stem}_${System.nanoTime()}.$extension"
        }
        return File(directory, unique)
    }
}

data class AnswerSharePlan(
    val text: String,
    val imagePaths: List<String>
) {
    val sharesText: Boolean = text.isNotBlank()
    val sharesImages: Boolean = imagePaths.isNotEmpty()
}

fun answerSharePlan(text: String, attachmentPaths: List<String>): AnswerSharePlan {
    val images = attachmentPaths.filter { path ->
        path.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
    }
    return AnswerSharePlan(text.trim(), images)
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
