package dev.chungjungsoo.gptmobile.presentation.common

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.chungjungsoo.gptmobile.presentation.ui.gguf.GgufModelsScreen

fun NavGraphBuilder.ggufModelsNavigation(navController: NavHostController) {
    composable(Route.GGUF_MODELS) {
        GgufModelsScreen(onNavigationClick = { navController.navigateUp() })
    }
}
