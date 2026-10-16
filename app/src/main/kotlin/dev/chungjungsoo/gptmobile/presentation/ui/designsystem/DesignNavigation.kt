package dev.chungjungsoo.gptmobile.presentation.ui.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.chungjungsoo.gptmobile.presentation.common.Route

fun NavHostController.openAppDestination(destination: AppDestination) {
    when (destination) {
        AppDestination.Chats -> popBackStack(Route.CHAT_LIST, inclusive = false)
        else -> navigate(destination.route()) { launchSingleTop = true }
    }
}

fun NavGraphBuilder.designNavigation(navController: NavHostController) {
    shellDestinations().forEach { destination ->
        composable(destination.route()) {
            CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                FeatureShellScreen(
                    destination = destination,
                    onDestination = navController::openAppDestination,
                    onBack = { navController.navigateUp() }
                )
            }
        }
    }
}
