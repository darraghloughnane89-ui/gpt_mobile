package dev.chungjungsoo.gptmobile.presentation.phone

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.phone.AttachmentNames
import java.io.File

/**
 * System camera capture. The photo is copied into the app attachments
 * directory and then handed to the existing image draft path.
 */
object CameraCapture {
    fun createPhotoFile(context: Context): File {
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(directory, "photo_${System.currentTimeMillis()}.jpg")
    }

    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    fun persist(context: Context, photo: File): String? {
        if (!photo.exists() || photo.length() <= 0L) {
            photo.delete()
            return null
        }
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        val safeName = AttachmentNames.sanitize(photo.name, "photo_${System.currentTimeMillis()}.jpg")
        val target = File(directory, safeName)
        return try {
            photo.copyTo(target, overwrite = true)
            photo.delete()
            target.absolutePath
        } catch (_: Exception) {
            photo.delete()
            null
        }
    }
}

@Composable
fun rememberCameraPhotoLauncher(onPhotoReady: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingFile
        pendingFile = null
        if (file == null) return@rememberLauncherForActivityResult
        if (success) {
            CameraCapture.persist(context, file)?.let(onPhotoReady) ?: file.delete()
        } else {
            file.delete()
        }
    }
    return {
        val file = CameraCapture.createPhotoFile(context)
        pendingFile = file
        try {
            launcher.launch(CameraCapture.uri(context, file))
        } catch (_: ActivityNotFoundException) {
            pendingFile = null
            file.delete()
            Toast.makeText(context, R.string.camera_failed, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun ChatCameraCapture(
    launchRequested: Boolean,
    onLaunchConsumed: () -> Unit,
    onPhotoReady: (String) -> Unit
) {
    val openCamera = rememberCameraPhotoLauncher(onPhotoReady)
    LaunchedEffect(launchRequested) {
        if (!launchRequested) return@LaunchedEffect
        onLaunchConsumed()
        openCamera()
    }
}
