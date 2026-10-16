package dev.chungjungsoo.gptmobile.presentation.phone

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import dev.chungjungsoo.gptmobile.data.phone.ShareIntentParser

fun Intent.toShareSnapshot(contentResolver: ContentResolver): ShareIntentParser.Snapshot {
    val uris = linkedSetOf<Uri>()
    when (action) {
        Intent.ACTION_SEND -> {
            IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
        }
        Intent.ACTION_SEND_MULTIPLE -> {
            IntentCompat.getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
                ?.let { uris += it }
        }
    }
    if (uris.isEmpty()) {
        val clip = clipData
        if (clip != null) {
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let { uris += it }
            }
        }
    }
    val text = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        ?: clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
    return ShareIntentParser.Snapshot(
        action = action,
        mimeType = type,
        extraText = text,
        streamUris = uris.map { it.toString() },
        streamMimeTypes = uris.map { uri -> runCatching { contentResolver.getType(uri) }.getOrNull() }
    )
}
