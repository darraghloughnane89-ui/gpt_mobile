package dev.chungjungsoo.gptmobile.presentation.ui.project

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.chungjungsoo.gptmobile.presentation.common.Route

fun NavGraphBuilder.projectsNavigation(navController: NavHostController) {
    composable(Route.PROJECTS) {
        ProjectsScreen(
            onBack = { navController.navigateUp() },
            onOpenProject = { projectId -> navController.navigate(Route.projectDetail(projectId)) },
            onCreateProject = { navController.navigate(Route.projectEditor()) }
        )
    }
    composable(
        route = Route.PROJECT_DETAIL,
        arguments = listOf(navArgument("projectId") { type = NavType.IntType })
    ) {
        ProjectDetailScreen(
            onBack = { navController.navigateUp() },
            onEdit = { projectId -> navController.navigate(Route.projectEditor(projectId)) },
            onDeleted = {
                navController.popBackStack(Route.PROJECTS, inclusive = false)
            },
            onOpenChat = { chat ->
                navController.navigate(
                    Route.chatRoomRoute(
                        chatRoomId = chat.id,
                        enabledPlatforms = chat.enabledPlatform.joinToString(",")
                    )
                )
            },
            onNewChat = { projectId, enabledPlatforms ->
                navController.navigate(
                    Route.chatRoomRoute(
                        chatRoomId = 0,
                        enabledPlatforms = enabledPlatforms.joinToString(","),
                        projectId = projectId
                    )
                )
            }
        )
    }
    composable(
        route = Route.PROJECT_EDITOR,
        arguments = listOf(
            navArgument("projectId") {
                type = NavType.IntType
                defaultValue = -1
            }
        )
    ) {
        ProjectEditorScreen(
            onBack = { navController.navigateUp() },
            onSaved = { projectId ->
                navController.navigate(Route.projectDetail(projectId)) {
                    popUpTo(Route.PROJECTS)
                }
            }
        )
    }
}
