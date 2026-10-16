package dev.chungjungsoo.gptmobile.presentation.ui.phonecontrol

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.chungjungsoo.gptmobile.presentation.common.Route

fun NavGraphBuilder.phoneControlNavigation(navController: NavHostController) {
    composable(
        route = Route.PHONE_CONTROL,
        arguments = listOf(
            navArgument("chatRoomId") {
                type = NavType.IntType
                defaultValue = -1
            }
        )
    ) { entry ->
        PhoneControlScreen(
            chatRoomId = entry.arguments?.getInt("chatRoomId") ?: -1,
            onBack = { navController.navigateUp() },
            onAllowlist = { navController.navigate(Route.PHONE_ALLOWLIST) },
            onActionLog = { navController.navigate(Route.PHONE_ACTION_LOG) }
        )
    }
    composable(Route.PHONE_ALLOWLIST) {
        PhoneAllowlistScreen(onBack = { navController.navigateUp() })
    }
    composable(Route.PHONE_ACTION_LOG) {
        PhoneActionLogScreen(onBack = { navController.navigateUp() })
    }
}
