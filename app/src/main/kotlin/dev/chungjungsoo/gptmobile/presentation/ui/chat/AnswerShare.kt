package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.attachment.answerSharePlan
import dev.chungjungsoo.gptmobile.util.FileUtils
import java.io.File

fun shareAnswer(context: Context, text: String, attachmentPaths: List<String>) {
    val plan = answerSharePlan(text, attachmentPaths)
    if (!plan.sharesText && !plan.sharesImages) return
    val imageFiles = plan.imagePaths.map(::File).filter { it.isFile }
    val uris = imageFiles.map { file ->
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val intent = when {
        uris.isEmpty() -> Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, plan.text)
        }

        !plan.sharesText && uris.size == 1 -> Intent(Intent.ACTION_SEND).apply {
            type = FileUtils.getMimeTypeFromPath(imageFiles.first().absolutePath)
            putExtra(Intent.EXTRA_STREAM, uris.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = if (plan.sharesText) "*/*" else "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            if (plan.sharesText) putExtra(Intent.EXTRA_TEXT, plan.text)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.share_answer)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val targets = context.packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)
    uris.forEach { uri ->
        targets.forEach { target ->
            context.grantUriPermission(
                target.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }
    context.startActivity(chooser)
}

fun saveImageToGallery(context: Context, path: String): Boolean {
    val source = File(path)
    if (!source.isFile) return false
    val mimeType = FileUtils.getMimeTypeFromPath(source.absolutePath).takeIf { it.startsWith("image/") } ?: return false
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/GPTMobile")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: return false
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}
