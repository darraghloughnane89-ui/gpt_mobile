package dev.chungjungsoo.gptmobile.data.phone

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.util.FileUtils
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportedShare(
    val composerText: String,
    val filePaths: List<String>,
    val warnings: List<String>
)

class SharedAttachmentImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pdfPageImageRenderer: PdfPageImageRenderer
) {
    suspend fun import(parsed: ShareIntentParser.Parsed): ImportedShare = withContext(Dispatchers.IO) {
        val files = mutableListOf<String>()
        val sections = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (parsed.rejected > 0) {
            warnings += "Skipped ${parsed.rejected} unsupported item(s)."
        }
        parsed.textFileUris.forEach { uri ->
            readTextFile(uri)?.let { sections += it } ?: warnings.add("Could not read a text file.")
        }
        parsed.imageUris.forEach { uri ->
            if (files.size >= ShareIntentParser.MAX_STREAMS) return@forEach
            copyToAttachments(uri, "image.jpg")?.let { files += it } ?: warnings.add("Could not read an image.")
        }
        parsed.pdfUris.forEach { uri ->
            val copied = copyToAttachments(uri, "document.pdf")
            if (copied == null) {
                warnings += "Could not read a PDF."
                return@forEach
            }
            val pdfFile = File(copied)
            val extracted = PdfTextExtractor.extract(pdfFile.readBytes())
            when (val plan = PdfSharePlanner.plan(pdfFile.name, extracted)) {
                is PdfSharePlanner.PdfPlan.Text -> {
                    sections += plan.composerSection
                    if (withinBudget(files, copied)) files += copied
                }
                PdfSharePlanner.PdfPlan.NeedsPageImages -> {
                    val pages = runCatching { pdfPageImageRenderer.render(pdfFile) }.getOrDefault(emptyList())
                    if (pages.isEmpty()) {
                        warnings += "No text could be read from ${pdfFile.name}."
                        pdfFile.delete()
                    } else {
                        sections += PdfSharePlanner.pageImageNote(pdfFile.name)
                        pages.forEach { page ->
                            if (withinBudget(files, page)) files += page
                        }
                        pdfFile.delete()
                    }
                }
            }
        }
        ImportedShare(
            composerText = PdfSharePlanner.compose(parsed.text, sections),
            filePaths = files,
            warnings = warnings
        )
    }

    private fun withinBudget(existing: List<String>, nextPath: String): Boolean {
        val current = existing.sumOf { File(it).length().coerceAtLeast(0L) }
        val next = File(nextPath).length().coerceAtLeast(0L)
        return !FileUtils.wouldExceedTotalUploadLimit(current, next)
    }

    private fun readTextFile(uriString: String): String? = try {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
            input.readBytes().toString(Charsets.UTF_8).take(PdfTextExtractor.MAX_CHARS)
        }
    } catch (_: Exception) {
        null
    }

    private fun copyToAttachments(uriString: String, fallbackName: String): String? = try {
        val uri = Uri.parse(uriString)
        val displayName = queryDisplayName(uri) ?: fallbackName
        val safeName = AttachmentNames.sanitize(displayName, fallbackName)
        val directory = File(context.filesDir, "attachments").apply { mkdirs() }
        var target = File(directory, safeName)
        if (target.exists()) {
            val stem = safeName.substringBeforeLast('.')
            val extension = safeName.substringAfterLast('.', "")
            val unique = if (extension.isNotEmpty()) {
                "${stem}_${System.currentTimeMillis()}.$extension"
            } else {
                "${safeName}_${System.currentTimeMillis()}"
            }
            target = File(directory, unique)
        }
        val directoryCanonical = directory.canonicalPath
        val targetCanonical = target.canonicalPath
        if (!targetCanonical.startsWith(directoryCanonical + File.separator)) return null
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        if (target.length() <= 0L || target.length() > FileUtils.MAX_UPLOAD_SIZE_BYTES) {
            target.delete()
            return null
        }
        target.absolutePath
    } catch (_: Exception) {
        null
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
}
