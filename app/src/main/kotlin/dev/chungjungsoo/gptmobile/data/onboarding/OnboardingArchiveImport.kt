package dev.chungjungsoo.gptmobile.data.onboarding

import android.net.Uri
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveGateway
import dev.chungjungsoo.gptmobile.data.project.archive.ProjectArchiveOutcome
import javax.inject.Inject

interface OnboardingArchiveImport {
    suspend fun importZip(uri: Uri): ProjectArchiveOutcome

    suspend fun importFolder(uri: Uri): ProjectArchiveOutcome
}

class GatewayOnboardingArchiveImport @Inject constructor(
    private val gateway: ProjectArchiveGateway
) : OnboardingArchiveImport {
    override suspend fun importZip(uri: Uri): ProjectArchiveOutcome = gateway.importZip(uri)

    override suspend fun importFolder(uri: Uri): ProjectArchiveOutcome = gateway.importFolder(uri)
}
