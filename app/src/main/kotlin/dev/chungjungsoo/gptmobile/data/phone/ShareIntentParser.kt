package dev.chungjungsoo.gptmobile.data.phone

import dev.chungjungsoo.gptmobile.data.portable.PortableKind

/**
 * Classifies an ACTION_SEND or ACTION_SEND_MULTIPLE payload.
 * Android-specific intent reading stays in [ShareIntentSnapshots].
 */
object ShareIntentParser {
    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_SEND_MULTIPLE = "android.intent.action.SEND_MULTIPLE"
    const val MAX_STREAMS = 10

    data class Snapshot(
        val action: String?,
        val mimeType: String?,
        val extraText: String?,
        val streamUris: List<String> = emptyList(),
        val streamMimeTypes: List<String?> = emptyList()
    )

    data class Parsed(
        val text: String = "",
        val imageUris: List<String> = emptyList(),
        val pdfUris: List<String> = emptyList(),
        val textFileUris: List<String> = emptyList(),
        val portableUris: List<String> = emptyList(),
        val rejected: Int = 0
    ) {
        fun isEmpty(): Boolean = text.isBlank() && imageUris.isEmpty() && pdfUris.isEmpty() && textFileUris.isEmpty() && portableUris.isEmpty()
    }

    fun parse(snapshot: Snapshot): Parsed {
        if (snapshot.action != ACTION_SEND && snapshot.action != ACTION_SEND_MULTIPLE) return Parsed()
        val images = mutableListOf<String>()
        val pdfs = mutableListOf<String>()
        val textFiles = mutableListOf<String>()
        val portable = mutableListOf<String>()
        var rejected = 0
        val limit = snapshot.streamUris.take(MAX_STREAMS)
        rejected += (snapshot.streamUris.size - limit.size).coerceAtLeast(0)
        limit.forEachIndexed { index, uri ->
            val mime = snapshot.streamMimeTypes.getOrNull(index) ?: snapshot.mimeType
            when (classify(mime, uri)) {
                Kind.IMAGE -> images += uri
                Kind.PDF -> pdfs += uri
                Kind.TEXT -> textFiles += uri
                Kind.PORTABLE -> portable += uri
                Kind.REJECT -> rejected++
            }
        }
        return Parsed(
            text = snapshot.extraText.orEmpty().trim(),
            imageUris = images.distinct(),
            pdfUris = pdfs.distinct(),
            textFileUris = textFiles.distinct(),
            portableUris = portable.distinct(),
            rejected = rejected
        )
    }

    internal fun classify(mimeType: String?, uri: String): Kind {
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        val extension = uri.substringAfterLast('.', "").substringBefore('?').lowercase()
        return when {
            extension in PORTABLE_EXTENSIONS || mime in PORTABLE_MIMES -> Kind.PORTABLE
            mime.startsWith("image/") || extension in IMAGE_EXTENSIONS -> Kind.IMAGE
            mime == "application/pdf" || extension == "pdf" -> Kind.PDF
            mime.startsWith("text/") || extension == "txt" -> Kind.TEXT
            else -> Kind.REJECT
        }
    }

    enum class Kind { IMAGE, PDF, TEXT, PORTABLE, REJECT }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif")
    private val PORTABLE_EXTENSIONS = PortableKind.entries.map { it.extension }.toSet()
    private val PORTABLE_MIMES = PortableKind.entries.map { it.mime }.toSet()
}
