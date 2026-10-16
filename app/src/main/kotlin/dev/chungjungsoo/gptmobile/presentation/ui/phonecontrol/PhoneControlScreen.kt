package dev.chungjungsoo.gptmobile.presentation.ui.phonecontrol

import android.content.Intent
import android.os.CancellationSignal
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneControlScreen(
    chatRoomId: Int,
    onBack: () -> Unit,
    onAllowlist: () -> Unit,
    onActionLog: () -> Unit,
    viewModel: PhoneControlViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val vaultTitles by viewModel.vaultTitles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var goal by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val metrics = context.resources.displayMetrics

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.phone_control_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.go_back)) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(status)
            if (!viewModel.accessibilityEnabled()) {
                Text(stringResource(R.string.phone_control_setup))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text(stringResource(R.string.phone_control_enable_accessibility))
                }
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                    Text(stringResource(R.string.phone_control_enable_notifications))
                }
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                    intent.data = android.net.Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.phone_control_enable_autofill))
                }
            }
            OutlinedTextField(value = goal, onValueChange = { goal = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.phone_control_goal)) })
            Button(onClick = {
                viewModel.start(chatRoomId, goal, metrics.widthPixels, metrics.heightPixels)
            }) { Text(stringResource(R.string.phone_control_start)) }
            Button(onClick = viewModel::stop) { Text(stringResource(R.string.phone_control_stop)) }
            Button(onClick = onAllowlist) { Text(stringResource(R.string.phone_control_allowlist)) }
            Button(onClick = onActionLog) { Text(stringResource(R.string.phone_control_action_log)) }
            Button(onClick = {
                context.getSystemService(AutofillManager::class.java)?.commit()
                Toast.makeText(context, R.string.phone_control_save_password_manager, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.phone_control_save_password_manager)) }
            Text(stringResource(R.string.phone_control_vault_heading))
            Text(vaultTitles.ifBlank { stringResource(R.string.phone_control_vault_empty) })
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.phone_control_vault_title)) })
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(stringResource(R.string.phone_control_username)) })
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text(stringResource(R.string.phone_control_secret)) },
                visualTransformation = PasswordVisualTransformation()
            )
            OutlinedTextField(value = packageName, onValueChange = { packageName = it }, label = { Text(stringResource(R.string.phone_control_package)) })
            OutlinedTextField(value = domain, onValueChange = { domain = it }, label = { Text(stringResource(R.string.phone_control_domain)) })
            Button(onClick = {
                val bytes = secret.encodeToByteArray()
                secret = ""
                authenticate(context) {
                    val cert = dev.chungjungsoo.gptmobile.data.phonecontrol.PackageSignature.certSha256(context, packageName).orEmpty()
                    viewModel.saveVault(title, username, bytes, packageName, cert, domain)
                }
            }) { Text(stringResource(R.string.phone_control_save_vault)) }
            Button(onClick = viewModel::refreshVault) { Text(stringResource(R.string.phone_control_show_vault)) }
            Button(onClick = {
                authenticate(context) {
                    scope.launch {
                        viewModel.createPasskey(context, """{"rp":{"id":"$domain"},"user":{"id":"AA","name":"$username","displayName":"$title"},"challenge":"AAAA","pubKeyCredParams":[{"type":"public-key","alg":-7}]}""", true)
                    }
                }
            }) { Text(stringResource(R.string.phone_control_passkey)) }
            Button(onClick = { viewModel.setRoutineApproved(true) }) { Text(stringResource(R.string.phone_control_approve_routine)) }
        }
    }

    pending?.let { confirmation ->
        ModalBottomSheet(onDismissRequest = { viewModel.confirmPending(false, false) }) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(confirmation.result.summary)
                Text(confirmation.result.reason)
                Button(onClick = { viewModel.confirmPending(true, false) }) { Text(stringResource(R.string.phone_control_confirm)) }
                Button(onClick = {
                    authenticate(context) { viewModel.confirmPending(true, true) }
                }) { Text(stringResource(R.string.phone_control_fingerprint)) }
                TextButton(onClick = { viewModel.confirmPending(false, false) }) { Text(stringResource(android.R.string.cancel)) }
            }
        }
    }
}

private fun authenticate(context: android.content.Context, onSuccess: () -> Unit) {
    val activity = context as? android.app.Activity ?: return
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
        .setTitle(activity.getString(R.string.phone_control_biometric_title))
        .setNegativeButton(activity.getString(android.R.string.cancel), executor) { _, _ -> }
        .build()
    prompt.authenticate(
        CancellationSignal(),
        executor,
        object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    )
}
