package dev.chungjungsoo.gptmobile.presentation.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.effectiveContent
import dev.chungjungsoo.gptmobile.data.voice.HandsFreePhase
import dev.chungjungsoo.gptmobile.data.voice.WakePhrase
import dev.chungjungsoo.gptmobile.presentation.ui.chat.ChatViewModel
import dev.chungjungsoo.gptmobile.presentation.voice.HandsFreeForegroundService
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandsFreeScreen(
    onBackAction: () -> Unit,
    chatViewModel: ChatViewModel = hiltViewModel(),
    handsFreeViewModel: HandsFreeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val session by handsFreeViewModel.state.collectAsStateWithLifecycle()
    val pending by handsFreeViewModel.pendingUtterance.collectAsStateWithLifecycle()
    val captureAllowed by handsFreeViewModel.captureAllowed.collectAsStateWithLifecycle()
    val grouped by chatViewModel.groupedMessages.collectAsStateWithLifecycle()
    val loading by chatViewModel.loadingStates.collectAsStateWithLifecycle()
    val contextBusy by chatViewModel.isContextBusy.collectAsStateWithLifecycle()
    val reply = grouped.assistantMessages.lastOrNull()?.firstOrNull()?.effectiveContent().orEmpty()
    val busy = contextBusy || loading.any { it is ChatViewModel.LoadingState.Loading }
    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    var waitingForNotification by remember { mutableStateOf(false) }
    val phase by rememberUpdatedState(session.phase)

    val beginConversation = {
        HandsFreeForegroundService.startConversation(context)
        waitingForNotification = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginConversation()
    }

    LaunchedEffect(Unit) {
        handsFreeViewModel.leaveToWake.collect { keepWake ->
            if (keepWake) {
                HandsFreeForegroundService.startWake(context)
            } else {
                HandsFreeForegroundService.stop(context)
            }
            onBackAction()
        }
    }

    LaunchedEffect(grouped.userMessages.size, reply, busy) {
        handsFreeViewModel.onChatSnapshot(grouped.userMessages.size, reply, busy)
    }

    LaunchedEffect(pending) {
        val utterance = pending ?: return@LaunchedEffect
        val accepted = chatViewModel.submitVoiceUtterance(utterance)
        handsFreeViewModel.consumeUtterance(accepted)
    }

    LaunchedEffect(captureAllowed, waitingForNotification) {
        if (waitingForNotification && captureAllowed) {
            delay(200)
            if (captureAllowed) {
                waitingForNotification = false
                handsFreeViewModel.startSession()
            }
        }
        if (!captureAllowed && phase != HandsFreePhase.IDLE && phase != HandsFreePhase.ENDED) {
            handsFreeViewModel.onCaptureBlocked()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hands_free_title)) },
                navigationIcon = {
                    IconButton(onClick = { handsFreeViewModel.leave() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.go_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = phaseLabel(session.phase, session.statusMessage),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = session.partialTranscript.ifBlank {
                    session.lastUserUtterance.ifBlank { stringResource(R.string.hands_free_prompt, WakePhrase.DISPLAY) }
                },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Start
            )
            Text(
                text = session.replyText.ifBlank { stringResource(R.string.hands_free_reply_placeholder) },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (!micGranted) {
                Text(
                    text = stringResource(R.string.hands_free_permission),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Button(
                onClick = {
                    when (session.phase) {
                        HandsFreePhase.SPEAKING, HandsFreePhase.THINKING -> handsFreeViewModel.interrupt()
                        HandsFreePhase.LISTENING -> Unit
                        else -> {
                            if (micGranted) {
                                beginConversation()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                Text(
                    text = primaryLabel(session.phase),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { handsFreeViewModel.toggleMute() },
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp)
                ) {
                    Text(
                        text = if (session.muted) {
                            stringResource(R.string.hands_free_unmute)
                        } else {
                            stringResource(R.string.hands_free_mute)
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Button(
                    onClick = { handsFreeViewModel.leave() },
                    modifier = Modifier
                        .weight(1f)
                        .height(96.dp)
                ) {
                    Text(
                        text = stringResource(R.string.hands_free_end),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun phaseLabel(phase: HandsFreePhase, status: String?): String {
    if (!status.isNullOrBlank()) return status
    return when (phase) {
        HandsFreePhase.IDLE, HandsFreePhase.ENDED -> stringResource(R.string.hands_free_idle)
        HandsFreePhase.LISTENING -> stringResource(R.string.hands_free_listening)
        HandsFreePhase.THINKING -> stringResource(R.string.hands_free_thinking)
        HandsFreePhase.SPEAKING -> stringResource(R.string.hands_free_speaking)
        HandsFreePhase.PAUSED -> stringResource(R.string.hands_free_paused)
    }
}

@Composable
private fun primaryLabel(phase: HandsFreePhase): String = when (phase) {
    HandsFreePhase.SPEAKING, HandsFreePhase.THINKING -> stringResource(R.string.hands_free_interrupt)
    HandsFreePhase.LISTENING -> stringResource(R.string.hands_free_listening)
    else -> stringResource(R.string.hands_free_start)
}
