package dev.chungjungsoo.gptmobile.data.agent.tool.code

import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import java.util.ArrayDeque

internal const val MAX_JAVASCRIPT_ATTACHMENTS = 4

internal fun selectSpreadsheetTexts(
    attachments: List<ChatAttachment>,
    readBytes: (path: String, maxBytes: Int) -> ByteArray?
): List<JavascriptAttachment> {
    val chosen = ArrayDeque<JavascriptAttachment>()
    val seen = HashSet<String>()
    for (attachment in attachments.asReversed()) {
        if (chosen.size >= MAX_JAVASCRIPT_ATTACHMENTS) break
        val name = cleanLabel(attachment.resolvedDisplayName.ifBlank { "attachment" })
        val mimeType = cleanLabel(attachment.mimeType.ifBlank { "text/plain" })
        if (!SpreadsheetText.isTabular(name, mimeType)) continue
        val key = name.lowercase()
        if (!seen.add(key)) continue
        val workbook = SpreadsheetText.isWorkbook(name, mimeType)
        val maxBytes = if (workbook) SpreadsheetText.MAX_XLSX_BYTES else SpreadsheetText.MAX_CSV_BYTES
        if (workbook && attachment.sizeBytes > maxBytes) continue
        val path = attachment.preparedFilePath.ifBlank { attachment.localFilePath }
        if (path.isBlank()) continue
        val bytes = readBytes(path, maxBytes) ?: continue
        val text = SpreadsheetText.extract(name, mimeType, bytes) ?: continue
        chosen.addFirst(
            JavascriptAttachment(
                name = name,
                mimeType = mimeType,
                text = text.take(SpreadsheetText.MAX_CSV_BYTES)
            )
        )
    }
    return chosen.toList()
}

private fun cleanLabel(value: String): String = value.replace(Regex("[\\r\\n]"), " ").trim().take(120)
