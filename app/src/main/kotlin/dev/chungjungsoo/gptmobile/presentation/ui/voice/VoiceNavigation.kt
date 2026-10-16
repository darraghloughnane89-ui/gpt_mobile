package dev.chungjungsoo.gptmobile.presentation.ui.voice

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.presentation.common.Route
import dev.chungjungsoo.gptmobile.presentation.common.voiceSessionChatRoute
import dev.chungjungsoo.gptmobile.presentation.voice.VoiceLaunchSignals
import javax.inject.Inject

fun NavGraphBuilder.voiceNavigation(navController: NavHostController) {
    composable(Route.VOICE_SETTINGS) {
        VoiceAssistantSettingsScreen(onNavigationClick = { navController.navigateUp() })
    }
    composable(Route.VOICE_SESSION) {
        VoiceSessionEntry(navController)
    }
}

@Composable
fun VoiceSessionEntry(
    navController: NavHostController,
    viewModel: VoiceLaunchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val platforms = viewModel.enabledPlatformUids()
        if (platforms.isEmpty()) {
            Toast.makeText(context, R.string.voice_no_platform, Toast.LENGTH_LONG).show()
            navController.popBackStack()
            return@LaunchedEffect
        }
        navController.navigate(voiceSessionChatRoute(platforms)) {
            popUpTo(Route.VOICE_SESSION) { inclusive = true }
        }
    }
}

@Composable
fun VoiceLaunchEffect(
    navController: NavHostController,
    enabled: Boolean,
    viewModel: VoiceLaunchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        VoiceLaunchSignals.pending.collect { pending ->
            if (!pending) return@collect
            VoiceLaunchSignals.consume()
            val platforms = viewModel.enabledPlatformUids()
            if (platforms.isEmpty()) {
                Toast.makeText(context, R.string.voice_no_platform, Toast.LENGTH_LONG).show()
                return@collect
            }
            navController.navigate(voiceSessionChatRoute(platforms))
        }
    }
}

@HiltViewModel
class VoiceLaunchViewModel @Inject constructor(
    private val settingRepository: SettingRepository
) : ViewModel() {
    suspend fun enabledPlatformUids(): List<String> = settingRepository.fetchPlatformV2s()
        .filter { it.enabled }
        .map { it.uid }
}
