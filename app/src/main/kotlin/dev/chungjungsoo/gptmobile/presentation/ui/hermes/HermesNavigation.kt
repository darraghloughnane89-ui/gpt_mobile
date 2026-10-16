package dev.chungjungsoo.gptmobile.presentation.ui.hermes

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.chungjungsoo.gptmobile.presentation.common.Route

fun NavGraphBuilder.hermesNavigation(navController: NavHostController) {
    composable(Route.HERMES_SETTINGS) {
        val viewModel: HermesViewModel = hiltViewModel()
        val state by viewModel.form.collectAsStateWithLifecycle()
        HermesSettingsScreen(
            state = state,
            onEndpointChange = viewModel::onEndpointChange,
            onProfileChange = viewModel::onProfileChange,
            onTokenChange = viewModel::onTokenChange,
            onChatModelChange = viewModel::onChatModelChange,
            onTestConnection = viewModel::testConnection,
            onSave = viewModel::save,
            onOpenThread = { navController.navigate(Route.HERMES_THREAD) },
            onBack = { navController.navigateUp() }
        )
    }
    composable(Route.HERMES_THREAD) {
        val viewModel: HermesViewModel = hiltViewModel()
        val state by viewModel.threadUi.collectAsStateWithLifecycle()
        HermesThreadScreen(
            state = state,
            onBack = { navController.navigateUp() },
            onRetry = viewModel::retry,
            onNewThread = viewModel::newThread
        )
    }
}
