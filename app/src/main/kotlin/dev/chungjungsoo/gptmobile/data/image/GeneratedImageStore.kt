package dev.chungjungsoo.gptmobile.data.image

import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import java.io.File
import java.util.UUID

class GeneratedImageStore(
    private val directory: File
) {
    fun save(bytes: ByteArray, mimeType: String): ChatAttachment {
        directory.mkdirs()
        val extension = extensionForMime(mimeType)
        val file = File(directory, "generated-${UUID.randomUUID()}.$extension")
        val canonicalDirectory = directory.canonicalFile
        val canonicalFile = file.canonicalFile
        val directoryPath = canonicalDirectory.path + File.separator
        check(canonicalFile.path.startsWith(directoryPath))
        file.writeBytes(bytes)
        return ChatAttachment(
            localFilePath = file.absolutePath,
            preparedFilePath = file.absolutePath,
            displayName = file.name,
            mimeType = mimeType.substringBefore(';').trim().ifBlank { "image/png" },
            sizeBytes = bytes.size.toLong()
        )
    }
}
