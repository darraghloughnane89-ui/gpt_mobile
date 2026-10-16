package dev.chungjungsoo.gptmobile.presentation.ui.portable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import dev.chungjungsoo.gptmobile.presentation.common.Route
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Composable
fun PortableImportEffect(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    LaunchedEffect(navController) {
        when (mainViewModel.event.first()) {
            MainViewModel.SplashEvent.OpenIntro -> awaitRoute(navController, Route.GET_STARTED)
            MainViewModel.SplashEvent.OpenMigrate -> awaitRoute(navController, Route.MIGRATE_V2)
            MainViewModel.SplashEvent.OpenHome -> awaitRoute(navController, Route.CHAT_LIST)
        }
        PortableImportBus.events.collect {
            navController.navigate(Route.PORTABLE_IMPORT) { launchSingleTop = true }
        }
    }
}

private suspend fun awaitRoute(navController: NavHostController, route: String) {
    snapshotFlow { navController.currentDestination?.route }
        .filterNotNull()
        .first { it == route }
}
