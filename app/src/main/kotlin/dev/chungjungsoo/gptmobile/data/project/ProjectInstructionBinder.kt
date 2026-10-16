package dev.chungjungsoo.gptmobile.data.project

import dev.chungjungsoo.gptmobile.data.context.ContextBuilder
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Fills the project slot and folds it into the platform prompt used for a run.
 * Compaction already fingerprints [PlatformV2.systemPrompt], so the project text is included.
 */
class ProjectInstructionBinder @Inject constructor(
    private val projectContextProvider: ProjectContextProvider,
    private val projectRepository: ProjectRepository,
    private val contextBuilder: ContextBuilder
) {
    suspend fun apply(platform: PlatformV2, projectId: Int?): PlatformV2 {
        if (projectId == null || projectId <= 0) return platform
        val layer = try {
            projectContextProvider.projectLayer(projectId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return platform
        }
        return contextBuilder.applyProjectLayer(platform, layer)
    }

    suspend fun projectName(projectId: Int?): String? {
        if (projectId == null || projectId <= 0) return null
        return try {
            projectRepository.getProject(projectId)?.name
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
}
