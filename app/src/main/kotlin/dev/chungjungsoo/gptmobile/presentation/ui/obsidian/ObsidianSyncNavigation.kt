package dev.chungjungsoo.gptmobile.presentation.ui.obsidian

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.chungjungsoo.gptmobile.presentation.common.Route

fun NavGraphBuilder.obsidianSyncNavigation(navController: NavHostController) {
    composable(Route.OBSIDIAN_SYNC) {
        ObsidianSyncRoute(onBack = { navController.navigateUp() })
    }
}

@Composable
fun ObsidianSyncRoute(
    onBack: () -> Unit,
    viewModel: ObsidianSyncViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            viewModel.useFolder(uri.toString())
        }
    }
    ObsidianSyncScreen(
        state = state,
        onBack = onBack,
        onOwnerChange = viewModel::onOwnerChange,
        onRepoChange = viewModel::onRepoChange,
        onBranchChange = viewModel::onBranchChange,
        onRootChange = viewModel::onRootChange,
        onTokenChange = viewModel::onTokenChange,
        onSaveGitHub = viewModel::saveGitHub,
        onSyncNow = viewModel::syncNow,
        onShowFolderOption = viewModel::onShowFolderOption,
        onUseGitHub = viewModel::useGitHub,
        onPickFolder = { folderPicker.launch(null) },
        onKeepPhone = viewModel::keepPhone,
        onKeepVault = viewModel::keepVault,
        onKeepBoth = viewModel::keepBoth
    )
}
