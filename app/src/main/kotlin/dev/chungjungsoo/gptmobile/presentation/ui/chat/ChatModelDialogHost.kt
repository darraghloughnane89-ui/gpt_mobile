package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.supportsRemoteModelList
import dev.chungjungsoo.gptmobile.presentation.ui.gguf.GgufModelsViewModel
import dev.chungjungsoo.gptmobile.presentation.ui.provider.RemoteModelCatalogViewModel
import dev.chungjungsoo.gptmobile.presentation.ui.setup.DownloadedLocalModelOption

@Composable
fun ChatModelDialogHost(
    platforms: List<PlatformV2>,
    platformOrder: List<String>,
    initialModels: Map<String, String>,
    platformNames: Map<String, String>,
    downloadedLocalModels: List<DownloadedLocalModelOption>,
    onNavigateToLocalModels: () -> Unit,
    onNavigateToGgufModels: () -> Unit,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Map<String, String>) -> Unit,
    catalog: RemoteModelCatalogViewModel = hiltViewModel(),
    ggufModelsViewModel: GgufModelsViewModel = hiltViewModel()
) {
    val catalogStates by catalog.states.collectAsStateWithLifecycle()
    val ggufState by ggufModelsViewModel.uiState.collectAsStateWithLifecycle()
    val relevant = platforms.filter { it.uid in platformOrder }
    LaunchedEffect(relevant.joinToString { "${it.uid}|${it.compatibleType}|${it.apiUrl}" }) {
        relevant.filter { it.compatibleType.supportsRemoteModelList() }.forEach { platform ->
            catalog.ensureLoaded(platform.compatibleType, platform.apiUrl, platform.token)
        }
    }
    val remoteModels = relevant.filter { it.compatibleType.supportsRemoteModelList() }.associate { platform ->
        val key = catalog.key(platform.compatibleType, platform.apiUrl)
        val models = catalogStates[key]?.models ?: catalog.defaults(platform.compatibleType)
        platform.uid to models
    }
    val remoteLoading = relevant.mapNotNull { platform ->
        val key = catalog.key(platform.compatibleType, platform.apiUrl)
        platform.uid.takeIf { catalogStates[key]?.isLoading == true }
    }.toSet()
    val remoteStatus = relevant.associate { platform ->
        val key = catalog.key(platform.compatibleType, platform.apiUrl)
        platform.uid to catalogStates[key]?.message
    }
    ChatModelDialog(
        platformOrder = platformOrder,
        initialModels = initialModels,
        platformNames = platformNames,
        platformClientTypes = relevant.associate { it.uid to it.compatibleType },
        downloadedLocalModels = downloadedLocalModels,
        onNavigateToLocalModels = onNavigateToLocalModels,
        remoteModels = remoteModels,
        remoteLoading = remoteLoading,
        remoteStatus = remoteStatus,
        onRefreshRemote = { uid ->
            val platform = relevant.firstOrNull { it.uid == uid } ?: return@ChatModelDialog
            catalog.refresh(platform.compatibleType, platform.apiUrl, platform.token)
        },
        ggufModels = ggufState.models,
        onNavigateToGgufModels = onNavigateToGgufModels,
        onDismissRequest = onDismissRequest,
        onConfirmRequest = onConfirmRequest
    )
}
