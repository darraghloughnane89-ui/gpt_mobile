package dev.chungjungsoo.gptmobile.presentation.ui.privacy

import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R

@Composable
fun AppLockGate(
    viewModel: AppLockViewModel = hiltViewModel(),
    onReady: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.resolved, state.locked) {
        if (state.resolved && state.locked) onReady()
    }
    val view = LocalView.current
    val activity = view.context as? FragmentActivity

    DisposableEffect(viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onEnteredBackground(System.currentTimeMillis())
                Lifecycle.Event.ON_START -> viewModel.onEnteredForeground(System.currentTimeMillis())
                else -> Unit
            }
        }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    SideEffect {
        val window = activity?.window ?: return@SideEffect
        if (state.flagSecure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(state.locked, state.promptRequested, state.deviceLockAvailable) {
        if (state.locked && state.promptRequested && state.deviceLockAvailable && activity != null) {
            AppLockAuthenticator.authenticate(activity, viewModel::onAuthenticationResult)
        }
    }

    LaunchedEffect(state.vaultPromptRequested, state.locked) {
        if (state.vaultPromptRequested && !state.locked && activity != null) {
            AppLockAuthenticator.authenticate(
                activity = activity,
                title = activity.getString(R.string.vault_unlock_title),
                subtitle = activity.getString(R.string.vault_unlock_subtitle),
                onResult = viewModel::onVaultAuthenticationResult
            )
        }
    }

    if (!state.resolved) return

    AppLockGateContent(
        locked = state.locked,
        deviceLockAvailable = state.deviceLockAvailable,
        onUnlock = viewModel::requestPrompt,
        onDisableUnavailable = viewModel::disableBecauseUnavailable,
        content = content
    )
}

@Composable
fun AppLockGateContent(
    locked: Boolean,
    deviceLockAvailable: Boolean,
    onUnlock: () -> Unit,
    onDisableUnavailable: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!locked) {
        content()
        return
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_lock_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = stringResource(
                    if (deviceLockAvailable) R.string.app_lock_gate_body else R.string.app_lock_unavailable
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            )
            if (deviceLockAvailable) {
                Button(onClick = onUnlock) {
                    Text(stringResource(R.string.app_lock_unlock))
                }
            } else {
                TextButton(onClick = onDisableUnavailable) {
                    Text(stringResource(R.string.app_lock_continue_without))
                }
            }
        }
    }
}
