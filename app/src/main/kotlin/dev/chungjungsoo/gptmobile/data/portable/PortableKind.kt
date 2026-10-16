package dev.chungjungsoo.gptmobile.data.portable

/**
 * Unsigned portable documents. A file is plain JSON with no signature.
 * The extension is part of the name (`notes.robin-bot`), so the suffix after
 * the last dot is `robin-bot`.
 */
enum class PortableKind(
    val wire: String,
    val extension: String,
    val mime: String
) {
    BOT("bot", "robin-bot", "application/vnd.robin.bot+json"),
    SKILL("skill", "robin-skill", "application/vnd.robin.skill+json"),
    PROMPT("prompt", "robin-prompt", "application/vnd.robin.prompt+json");

    companion object {
        fun fromWire(wire: String?): PortableKind? = entries.firstOrNull { it.wire == wire }

        fun fromExtension(extension: String?): PortableKind? =
            entries.firstOrNull { it.extension == extension?.lowercase() }

        fun fromMime(mime: String?): PortableKind? {
            val normalized = mime?.substringBefore(';')?.trim()?.lowercase()
            return entries.firstOrNull { it.mime == normalized }
        }

        fun fromFileName(fileName: String?): PortableKind? {
            if (fileName.isNullOrBlank()) return null
            val leaf = fileName.substringAfterLast('/').substringAfterLast('\\')
            return fromExtension(leaf.substringAfterLast('.', ""))
        }
    }
}

object PortableLimits {
    const val FORMAT = "robin.portable"
    const val VERSION = 1
    const val SCHEME = "robin"
    const val HOST_IMPORT = "import"
    const val QUERY = "p"
    const val MAX_DOCUMENT_BYTES = 256 * 1024
    const val MAX_LINK_PAYLOAD_CHARS = 12_000
    const val MAX_LIBRARY_ITEMS = 200
    const val MAX_NAME = 80
    const val MAX_SKILL_NAME = 64
    const val MAX_DESCRIPTION = 1024
    const val MAX_INSTRUCTIONS = 8_000
    const val MAX_TOOLS = 32
    const val MAX_TOOL_NAME = 64
    const val MAX_EXAMPLES = 8
    const val MAX_EXAMPLE = 400
}
