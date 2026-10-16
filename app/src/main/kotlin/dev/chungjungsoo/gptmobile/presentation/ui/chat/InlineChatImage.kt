package dev.chungjungsoo.gptmobile.presentation.ui.chat

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.image.GalleryImageSaver
import dev.chungjungsoo.gptmobile.data.image.inSampleSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun InlineChatImage(
    filePath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageDescription = stringResource(R.string.generated_image)
    val actionsLabel = stringResource(R.string.image_actions)
    var bitmap by remember(filePath) { mutableStateOf<ImageBitmap?>(null) }
    var menuOpen by remember(filePath) { mutableStateOf(false) }
    LaunchedEffect(filePath) {
        bitmap = withContext(Dispatchers.IO) { decodeChatImage(filePath) }
    }
    val decoded = bitmap ?: return
    Box(modifier = modifier) {
        Image(
            bitmap = decoded,
            contentDescription = imageDescription,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .heightIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .semantics {
                    contentDescription = imageDescription
                    onLongClick(label = actionsLabel) {
                        menuOpen = true
                        true
                    }
                }
                .pointerInput(filePath) {
                    detectTapGestures(onLongPress = { menuOpen = true })
                }
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_image)) },
                onClick = {
                    menuOpen = false
                    shareChatImage(context, File(filePath))
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.save_image_to_gallery)) },
                onClick = {
                    menuOpen = false
                    val saved = runCatching {
                        GalleryImageSaver.save(context, File(filePath), mimeTypeForPath(filePath))
                    }.getOrDefault(false)
                    val message = if (saved) {
                        context.getString(R.string.image_saved_to_gallery)
                    } else {
                        context.getString(R.string.image_save_failed)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

internal fun decodeChatImage(filePath: String, maxDimension: Int = 1024): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(filePath, bounds)
    val sample = inSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(filePath, options)?.asImageBitmap()
}

internal fun shareChatImage(context: Context, file: File) {
    if (!file.isFile) {
        Toast.makeText(context, context.getString(R.string.image_save_failed), Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = mimeTypeForPath(file.path)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        }
        context.startActivity(Intent.createChooser(share, context.getString(R.string.share_image)))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.image_save_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun mimeTypeForPath(path: String): String = when (File(path).extension.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    else -> "image/png"
}
