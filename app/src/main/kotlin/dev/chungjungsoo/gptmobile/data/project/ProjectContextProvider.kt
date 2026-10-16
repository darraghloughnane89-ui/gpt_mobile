package dev.chungjungsoo.gptmobile.data.project

/**
 * Supplies the project slot of [dev.chungjungsoo.gptmobile.data.context.InstructionStack].
 * A missing project, or a project with no instructions and no file text, returns an empty layer.
 */
fun interface ProjectContextProvider {
    suspend fun projectLayer(projectId: Int?): String
}
