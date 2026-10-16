package dev.chungjungsoo.gptmobile.data.security

import java.io.File

internal class SecretMetadataStore(private val directory: File) {
    fun write(entry: SecretEntry) {
        directory.mkdirs()
        val file = File(directory, "${entry.id}.meta")
        file.writeText(
            listOf(
                entry.type.name,
                entry.label.replace("\n", " ").replace("\t", " "),
                entry.masked.replace("\n", " "),
                entry.updatedAtEpochMs.toString(),
                entry.endpoint.replace("\n", " ")
            ).joinToString("\n")
        )
    }

    fun delete(id: String) {
        File(directory, "$id.meta").delete()
    }

    fun list(): List<SecretEntry> {
        if (!directory.isDirectory) return emptyList()
        val fromMeta = directory.listFiles { file -> file.isFile && file.extension == "meta" }
            .orEmpty()
            .mapNotNull(::parse)
        val known = fromMeta.map { it.id }.toSet()
        val orphans = directory.listFiles { file -> file.isFile && file.extension == "vault" }
            .orEmpty()
            .map { it.nameWithoutExtension }
            .filter { it !in known }
            .map { id ->
                SecretEntry(
                    id = id,
                    type = SecretType.API_KEY,
                    label = id,
                    masked = "••••"
                )
            }
        return (fromMeta + orphans).sortedBy { it.label }
    }

    private fun parse(file: File): SecretEntry? {
        val lines = file.readLines()
        if (lines.size < 4) return null
        val type = runCatching { SecretType.valueOf(lines[0]) }.getOrNull() ?: return null
        return SecretEntry(
            id = file.nameWithoutExtension,
            type = type,
            label = lines[1],
            masked = lines[2],
            updatedAtEpochMs = lines[3].toLongOrNull() ?: 0L,
            endpoint = lines.getOrNull(4).orEmpty()
        )
    }
}
