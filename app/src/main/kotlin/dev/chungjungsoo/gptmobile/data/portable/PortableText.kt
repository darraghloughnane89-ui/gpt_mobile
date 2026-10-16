package dev.chungjungsoo.gptmobile.data.portable

/**
 * Imported text is data. Control characters, byte-order marks, and bidi
 * overrides are removed so a preview cannot hide or reverse what the person
 * is about to keep.
 */
fun untrustedText(raw: String, max: Int): String {
    if (max <= 0) return ""
    val out = StringBuilder(minOf(raw.length, max))
    raw.forEach { ch ->
        if (out.length >= max) return@forEach
        val keep = when {
            ch == '\n' || ch == '\t' -> true
            ch == '\r' || ch == '\uFEFF' -> false
            ch.code < 0x20 || ch.code == 0x7F -> false
            ch.code in 0x202A..0x202E -> false
            ch.code in 0x2066..0x2069 -> false
            ch == '\u200B' || ch == '\u200C' || ch == '\u200D' -> false
            else -> true
        }
        if (keep) out.append(ch)
    }
    return out.toString().trim()
}

fun portableSlug(raw: String, max: Int = PortableLimits.MAX_SKILL_NAME): String? {
    val normalized = raw.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(max)
        .trim('-')
    if (normalized.isEmpty() || !SLUG.matches(normalized)) return null
    return normalized
}

fun portableFileName(title: String, kind: PortableKind): String {
    val slug = portableSlug(title, PortableLimits.MAX_NAME) ?: "item"
    return "$slug.${kind.extension}"
}

private val SLUG = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
