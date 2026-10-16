package dev.chungjungsoo.gptmobile.presentation.phone

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.phone.PhoneDeepLinkRouter
import dev.chungjungsoo.gptmobile.data.phone.PhoneHandoffStore
import dev.chungjungsoo.gptmobile.data.phone.PhoneLinks
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.presentation.common.Route
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@HiltViewModel
class PhoneDeepLinkViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val chatRepository: ChatRepository,
    private val handoffStore: PhoneHandoffStore
) : ViewModel() {
    suspend fun enabledPlatformUids(): List<String> = settingRepository.fetchPlatformV2s()
        .filter { it.enabled }
        .map { it.uid }

    suspend fun platformsForChat(chatId: Int): List<String> = chatRepository.fetchChatListV2()
        .firstOrNull { it.id == chatId }
        ?.enabledPlatform
        .orEmpty()

    fun offerCameraForNewChat() {
        handoffStore.offer(
            PhoneHandoffStore.Handoff(
                targetChatId = 0,
                launchCamera = true
            )
        )
    }

    fun offerMicForNewChat() {
        handoffStore.offer(
            PhoneHandoffStore.Handoff(
                targetChatId = 0,
                requestMic = true
            )
        )
    }
}

fun chatRoomRoute(chatRoomId: Int, enabledPlatforms: List<String>, projectId: Int = -1): String =
    Route.chatRoomRoute(
        chatRoomId = chatRoomId,
        enabledPlatforms = enabledPlatforms.joinToString(","),
        projectId = projectId
    )

@Composable
fun PhoneDeepLinkEffect(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel(),
    phoneViewModel: PhoneDeepLinkViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(navController) {
        when (mainViewModel.event.first()) {
            MainViewModel.SplashEvent.OpenIntro -> navController.awaitRoute(Route.GET_STARTED)
            MainViewModel.SplashEvent.OpenMigrate -> navController.awaitRoute(Route.MIGRATE_V2)
            MainViewModel.SplashEvent.OpenHome -> navController.awaitRoute(Route.CHAT_LIST)
        }
        PhoneEntryBus.events.collect { intent ->
            val data = intent.data ?: return@collect
            val parts = PhoneLinks.parts(data.scheme, data.host, data.path, data.query)
            val enabled = phoneViewModel.enabledPlatformUids()
            val voiceReady = navController.hasDestination(PhoneLinks.VOICE_NAV_ROUTE)
            when (val destination = PhoneDeepLinkRouter.resolve(parts, enabled, voiceReady)) {
                is PhoneDeepLinkRouter.Destination.NewChat -> {
                    if (destination.launchCamera) phoneViewModel.offerCameraForNewChat()
                    if (destination.composerInput == PhoneDeepLinkRouter.Destination.ComposerInput.MIC) {
                        phoneViewModel.offerMicForNewChat()
                    }
                    navController.navigate(chatRoomRoute(0, destination.enabledPlatforms)) {
                        launchSingleTop = true
                    }
                }
                is PhoneDeepLinkRouter.Destination.OpenChat -> {
                    val platforms = destination.enabledPlatforms.ifEmpty {
                        phoneViewModel.platformsForChat(destination.chatId)
                    }
                    if (platforms.isEmpty()) {
                        Toast.makeText(context, R.string.chat_link_unavailable, Toast.LENGTH_SHORT).show()
                    } else {
                        navController.navigate(chatRoomRoute(destination.chatId, platforms)) {
                            launchSingleTop = true
                        }
                    }
                }
                PhoneDeepLinkRouter.Destination.Voice -> {
                    navController.navigate(PhoneLinks.VOICE_NAV_ROUTE) {
                        launchSingleTop = true
                    }
                }
                PhoneDeepLinkRouter.Destination.VoiceUnavailable -> {
                    Toast.makeText(context, R.string.voice_mode_unavailable, Toast.LENGTH_SHORT).show()
                }
                PhoneDeepLinkRouter.Destination.MissingPlatforms -> {
                    Toast.makeText(context, R.string.no_platforms_for_share, Toast.LENGTH_SHORT).show()
                }
                PhoneDeepLinkRouter.Destination.Ignore -> Unit
            }
        }
    }
}

private suspend fun NavHostController.awaitRoute(route: String) {
    snapshotFlow { currentDestination?.route }
        .filterNotNull()
        .first { it == route }
}

private fun NavHostController.hasDestination(route: String): Boolean = runCatching { graph.findNode(route) != null }
    .getOrDefault(false)
