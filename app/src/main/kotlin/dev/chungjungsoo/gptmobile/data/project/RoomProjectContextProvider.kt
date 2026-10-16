package dev.chungjungsoo.gptmobile.data.project

import javax.inject.Inject

class RoomProjectContextProvider @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val composer: ProjectContextComposer
) : ProjectContextProvider {
    override suspend fun projectLayer(projectId: Int?): String {
        if (projectId == null || projectId <= 0) return ""
        val project = projectRepository.getProject(projectId) ?: return ""
        return composer.compose(project.instructions, projectRepository.getFiles(projectId))
    }
}
