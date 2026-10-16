package dev.chungjungsoo.gptmobile.presentation.ui.skill

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.chungjungsoo.gptmobile.presentation.common.Route

fun NavGraphBuilder.skillNavigation(navController: NavHostController) {
    composable(Route.SKILLS) {
        val viewModel: SkillLibraryViewModel = hiltViewModel()
        SkillsRoute(
            viewModel = viewModel,
            onBack = { navController.navigateUp() },
            onOpenInbox = { navController.navigate(Route.SKILL_INBOX) },
            onCreate = { navController.navigate(skillEditorRoute(-1L)) },
            onEdit = { skillId -> navController.navigate(skillEditorRoute(skillId)) }
        )
    }
    composable(
        route = Route.SKILL_EDITOR,
        arguments = listOf(
            navArgument("skillId") {
                type = NavType.LongType
                defaultValue = -1L
            }
        )
    ) {
        val viewModel: SkillEditorViewModel = hiltViewModel()
        SkillEditorRoute(
            viewModel = viewModel,
            onBack = { navController.navigateUp() }
        )
    }
    composable(Route.SKILL_INBOX) {
        val viewModel: SkillInboxViewModel = hiltViewModel()
        SkillInboxRoute(
            viewModel = viewModel,
            onBack = { navController.navigateUp() }
        )
    }
}

fun skillEditorRoute(skillId: Long): String = "skills/edit?skillId=$skillId"
