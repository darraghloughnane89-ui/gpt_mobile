package dev.chungjungsoo.gptmobile.data.portable

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a content URI or a share into a preview. Bytes past the document cap
 * are refused before they are parsed.
 */
@Singleton
class PortableFileIntake @Inject constructor() {
    fun accepts(resolver: ContentResolver, uri: Uri, mime: String?): Boolean {
        val resolvedMime = mime ?: runCatching { resolver.getType(uri) }.getOrNull()
        if (PortableDocuments.declaredKind(uri.lastPathSegment, resolvedMime) != null) return true
        return PortableDocuments.declaredKind(displayName(resolver, uri), null) != null
    }

    fun read(resolver: ContentResolver, uri: Uri): PortableResult {
        val name = displayName(resolver, uri) ?: uri.lastPathSegment
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        val bytes = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                PortablePayload.readCapped(stream, PortableLimits.MAX_DOCUMENT_BYTES + 1)
            }
        }.getOrNull() ?: return PortableResult.Failure(PortableFailure.NOT_A_DOCUMENT)
        if (bytes.size > PortableLimits.MAX_DOCUMENT_BYTES) {
            return PortableResult.Failure(PortableFailure.TOO_LARGE)
        }
        return PortableDocuments.consider(bytes.toString(Charsets.UTF_8), name, mime)
            ?: PortableResult.Failure(PortableFailure.NOT_A_DOCUMENT)
    }

    fun fromShareText(text: String?, fileName: String? = null, mime: String? = null): PortableResult? =
        PortableDocuments.consider(text, fileName, mime)

    private fun displayName(resolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
        }.getOrNull()
    }
}
