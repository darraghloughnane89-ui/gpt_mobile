package dev.chungjungsoo.gptmobile.presentation.common

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.chungjungsoo.gptmobile.presentation.ui.memory.MemoryScreen
import dev.chungjungsoo.gptmobile.presentation.ui.privacy.AppLockScreen
import dev.chungjungsoo.gptmobile.presentation.ui.privacy.KeyManagerScreen
import dev.chungjungsoo.gptmobile.presentation.ui.usage.UsageScreen

fun NavGraphBuilder.privacyNavigation(navController: NavHostController) {
    composable(Route.MEMORY) {
        MemoryScreen(onBack = { navController.navigateUp() })
    }
    composable(Route.USAGE) {
        UsageScreen(onBack = { navController.navigateUp() })
    }
    composable(Route.APP_LOCK) {
        AppLockScreen(onBack = { navController.navigateUp() })
    }
    composable(Route.KEY_MANAGER) {
        KeyManagerScreen(onBack = { navController.navigateUp() })
    }
}
