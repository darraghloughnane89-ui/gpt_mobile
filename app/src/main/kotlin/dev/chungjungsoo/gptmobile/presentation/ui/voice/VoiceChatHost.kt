package dev.chungjungsoo.gptmobile.presentation.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.diagnostics.displayChatText
import dev.chungjungsoo.gptmobile.data.diagnostics.presentChatFailure
import dev.chungjungsoo.gptmobile.data.voice.VoicePhase
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatViewModel

@Composable
fun VoiceChatHost(
    chatViewModel: ChatViewModel,
    onDismiss: () -> Unit,
    voiceViewModel: VoiceSessionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var permissionGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
    }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val state by voiceViewModel.state.collectAsStateWithLifecycle()
    val groupedMessages by chatViewModel.groupedMessages.collectAsStateWithLifecycle()
    val loadingStates by chatViewModel.loadingStates.collectAsStateWithLifecycle()
    val contextBusy by chatViewModel.isContextBusy.collectAsStateWithLifecycle()
    val chatContext by chatViewModel.chatContextState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) voiceViewModel.start()
    }
    LaunchedEffect(Unit) {
        voiceViewModel.submittedUtterances.collect { utterance ->
            if (!chatViewModel.submitVoiceUtterance(utterance)) {
                voiceViewModel.onUtteranceRejected()
            }
        }
    }
    LaunchedEffect(Unit) {
        voiceViewModel.replyCancellations.collect {
            chatViewModel.cancelActiveRuns()
        }
    }
    val rawAssistant = groupedMessages.assistantMessages.lastOrNull()
        ?.firstOrNull()
        ?.effectiveContent()
        .orEmpty()
    val generating = loadingStates.firstOrNull()?.let { it != ChatViewModel.LoadingState.Idle } == true
    LaunchedEffect(groupedMessages, loadingStates, contextBusy, chatContext) {
        voiceViewModel.onChatSnapshot(
            turnCount = groupedMessages.userMessages.size,
            assistantText = displayChatText(rawAssistant),
            busy = generating || contextBusy,
            awaitingContext = chatContext != null
        )
    }
    DisposableEffect(Unit) {
        onDispose { voiceViewModel.end() }
    }

    val failure = presentChatFailure(rawAssistant)
    val canRetryFailure = failure != null && !generating && !contextBusy && groupedMessages.userMessages.isNotEmpty()
    VoiceScreen(
        state = state,
        permissionGranted = permissionGranted,
        onMute = voiceViewModel::mute,
        onUnmute = voiceViewModel::unmute,
        onInterrupt = voiceViewModel::interrupt,
        onEnd = {
            voiceViewModel.end()
            onDismiss()
        },
        onOrbClick = {
            when (state.phase) {
                VoicePhase.PAUSED -> voiceViewModel.resume()
                VoicePhase.SPEAKING, VoicePhase.THINKING -> voiceViewModel.interrupt()
                else -> Unit
            }
        },
        onGrantPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        failureKind = failure?.kind,
        onRetry = if (canRetryFailure) {
            { chatViewModel.retryChat(groupedMessages.userMessages.lastIndex, 0) }
        } else {
            null
        }
    )
}
