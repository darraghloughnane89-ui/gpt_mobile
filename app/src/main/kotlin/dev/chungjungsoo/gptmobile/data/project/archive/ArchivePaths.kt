package dev.chungjungsoo.gptmobile.data.project.archive

internal object ArchivePaths {
    val reservedRoots = setOf("files", "chats")

    fun normalize(path: String): String? {
        val cleaned = path.replace('\\', '/').trim().trimStart('/')
        if (cleaned.isEmpty() || cleaned.endsWith("/")) return null
        val parts = cleaned.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        if (parts.first() == "__MACOSX" || parts.last() == ".DS_Store") return null
        return cleaned
    }

    /**
     * A single wrapper directory is removed, except the project folders themselves.
     * `Export/chats/a.md` becomes `chats/a.md`. `files/note.md` stays put.
     */
    fun topFolderPrefix(paths: List<String>): String {
        if (paths.isEmpty() || paths.any { !it.contains('/') }) return ""
        val roots = paths.map { it.substringBefore('/') }.toSet()
        if (roots.size != 1) return ""
        val root = roots.single()
        if (root in reservedRoots) return ""
        return "$root/"
    }

    fun extension(path: String): String = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()

    fun fileName(path: String): String = path.substringAfterLast('/').ifBlank { "file" }

    fun isZip(path: String): Boolean = extension(path) == "zip"

    fun isCatalog(path: String): Boolean {
        if (path.startsWith("files/")) return false
        if (path.equals("files/import-map.json", ignoreCase = true)) return false
        if (path.startsWith("chats/")) return true
        val name = path.substringAfterLast('/').lowercase()
        if (name == "import-map.json") return false
        return extension(path) in CATALOG_EXTENSIONS || name in STRUCTURAL_NAMES
    }

    fun isStructural(path: String): Boolean {
        val name = path.substringAfterLast('/').lowercase()
        return path.startsWith("chats/") || name in STRUCTURAL_NAMES
    }

    private val CATALOG_EXTENSIONS = setOf("json", "md", "markdown", "txt")
    private val STRUCTURAL_NAMES = setOf(
        "manifest.json",
        "instructions.md",
        "conversations.json",
        "projects.json",
        "project.json"
    )
}
