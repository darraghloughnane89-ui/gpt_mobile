package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.plugin.ToolConfirmBroker

@Composable
fun PluginWriteConfirmHost() {
    val broker = rememberPluginConfirmBroker()
    val pending by broker.pending.collectAsStateWithLifecycle()
    DisposableEffect(broker) {
        broker.uiAttached = true
        onDispose {
            broker.uiAttached = false
            broker.answer(false)
        }
    }
    val request = pending ?: return
    AlertDialog(
        onDismissRequest = { broker.answer(false) },
        title = { Text(stringResource(R.string.plugin_write_confirm_title)) },
        text = { Text(request.detail) },
        confirmButton = {
            TextButton(onClick = { broker.answer(true) }) {
                Text(stringResource(R.string.plugin_write_confirm_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = { broker.answer(false) }) {
                Text(stringResource(R.string.plugin_write_confirm_deny))
            }
        }
    )
}

@Composable
private fun rememberPluginConfirmBroker(): ToolConfirmBroker {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, PluginConfirmEntryPoint::class.java).toolConfirmBroker()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PluginConfirmEntryPoint {
    fun toolConfirmBroker(): ToolConfirmBroker
}
