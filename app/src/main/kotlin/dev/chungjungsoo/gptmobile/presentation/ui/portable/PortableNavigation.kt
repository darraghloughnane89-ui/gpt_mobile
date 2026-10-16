package dev.chungjungsoo.gptmobile.presentation.ui.portable

import android.widget.Toast
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.portable.PortableKind
import dev.chungjungsoo.gptmobile.presentation.common.Route

fun NavGraphBuilder.portableNavigation(navController: NavHostController) {
    composable(Route.PORTABLE_IMPORT) {
        val context = androidx.compose.ui.platform.LocalContext.current
        PortableImportScreen(
            onBack = { navController.navigateUp() },
            onImported = { kind ->
                val message = when (kind) {
                    PortableKind.BOT -> R.string.portable_imported_bot
                    PortableKind.SKILL -> R.string.portable_imported_skill
                    PortableKind.PROMPT -> R.string.portable_imported_prompt
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                navController.navigateUp()
            }
        )
    }
}

fun NavGraphBuilder.portableLibraryNavigation(navController: NavHostController) {
    composable(Route.PORTABLE_SKILLS) {
        PortableSkillListScreen(
            onBack = { navController.navigateUp() },
            onCreate = { navController.navigate(Route.portableSkillEditor(null)) },
            onEdit = { id -> navController.navigate(Route.portableSkillEditor(id)) },
            onImportPreview = { navController.navigate(Route.PORTABLE_IMPORT) }
        )
    }
    composable(
        Route.PORTABLE_SKILL_EDITOR,
        arguments = listOf(navArgument("skillId") { type = NavType.StringType })
    ) {
        PortableSkillEditorScreen(onBack = { navController.navigateUp() })
    }
    composable(Route.SAVED_PROMPTS) {
        SavedPromptListScreen(
            onBack = { navController.navigateUp() },
            onCreate = { navController.navigate(Route.savedPromptEditor(null)) },
            onEdit = { id -> navController.navigate(Route.savedPromptEditor(id)) },
            onImportPreview = { navController.navigate(Route.PORTABLE_IMPORT) }
        )
    }
    composable(
        Route.SAVED_PROMPT_EDITOR,
        arguments = listOf(navArgument("promptId") { type = NavType.StringType })
    ) {
        SavedPromptEditorScreen(onBack = { navController.navigateUp() })
    }
}
