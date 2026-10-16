package dev.chungjungsoo.gptmobile.presentation.ui.bot

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.chungjungsoo.gptmobile.presentation.common.Route

fun NavGraphBuilder.botNavigation(navController: NavHostController) {
    composable(Route.BOTS) {
        BotListScreen(
            onNavigationClick = { navController.navigateUp() },
            onCreateClick = { navController.navigate(Route.BOT_CREATE) },
            onEditClick = { botUid ->
                navController.navigate(Route.BOT_EDITOR.replace("{botUid}", botUid))
            }
        )
    }
    composable(Route.BOT_CREATE) {
        BotEditorScreen(
            onNavigationClick = { navController.navigateUp() },
            onSaved = { navController.navigateUp() }
        )
    }
    composable(
        Route.BOT_EDITOR,
        arguments = listOf(navArgument("botUid") { type = NavType.StringType })
    ) {
        BotEditorScreen(
            onNavigationClick = { navController.navigateUp() },
            onSaved = { navController.navigateUp() }
        )
    }
}
