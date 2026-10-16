package dev.chungjungsoo.gptmobile.presentation.ui.portable

import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import dev.chungjungsoo.gptmobile.data.portable.PortableKind
import dev.chungjungsoo.gptmobile.data.portable.portableFileName
import java.io.File

fun sharePortableDocument(
    context: android.content.Context,
    json: String,
    kind: PortableKind,
    title: String,
    chooserTitle: String
) {
    val dir = File(context.cacheDir, "portable").apply { mkdirs() }
    val file = File(dir, portableFileName(title, kind))
    file.writeText(json)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = kind.mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        clipData = ClipData.newRawUri(title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val handlers = context.packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)
    handlers.forEach { info ->
        context.grantUriPermission(
            info.activityInfo.packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
    context.startActivity(chooser)
}
