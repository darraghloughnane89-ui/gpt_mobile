package dev.chungjungsoo.gptmobile.data.project

import dev.chungjungsoo.gptmobile.data.database.entity.ProjectFile
import javax.inject.Inject

class ProjectContextComposer @Inject constructor() {
    fun compose(instructions: String, files: List<ProjectFile>): String {
        val instructionText = instructions.trim().take(ProjectContextLimits.MAX_INSTRUCTION_CHARS)
        val fileText = renderFiles(
            files = files,
            budget = (ProjectContextLimits.MAX_CONTEXT_CHARS - instructionText.length).coerceAtLeast(0)
        )
        return listOf(instructionText, fileText)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun renderFiles(files: List<ProjectFile>, budget: Int): String {
        if (files.isEmpty() || budget < 32) return ""
        val body = StringBuilder("Project files:")
        var remaining = budget - body.length
        files.forEach { file ->
            val block = fileBlock(file)
            if (block.length + 2 > remaining) {
                val notice = "\n\nFurther project files were omitted to stay within the context limit."
                if (notice.length <= remaining) body.append(notice)
                return body.toString()
            }
            body.append("\n\n").append(block)
            remaining -= block.length + 2
        }
        return body.toString()
    }

    private fun fileBlock(file: ProjectFile): String {
        val title = "### ${file.displayName}"
        val body = when (ProjectFileTypes.classify(file.mimeType, file.displayName)) {
            ProjectFileKind.IMAGE -> imageNote(file)
            ProjectFileKind.PDF -> file.extractedText?.trim().orEmpty().ifBlank {
                "No text could be extracted from this PDF."
            }
            ProjectFileKind.TEXT, null -> file.extractedText?.trim().orEmpty()
        }.take(ProjectContextLimits.MAX_FILE_CHARS)
        return if (body.isBlank()) title else "$title\n$body"
    }

    private fun imageNote(file: ProjectFile): String {
        val sizeKb = (file.sizeBytes / 1024).coerceAtLeast(1)
        return "Image file (${file.mimeType}, $sizeKb KB) stored with this project. The picture itself is not inlined."
    }
}
