package dev.chungjungsoo.gptmobile.presentation.ui.oauth

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.oauth.ProviderOAuthAppAuth
import dev.chungjungsoo.gptmobile.data.oauth.ProviderOAuthCoordinator
import dev.chungjungsoo.gptmobile.data.oauth.ProviderSignInStart
import dev.chungjungsoo.gptmobile.data.oauth.supportsProviderSignIn
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationService

@HiltViewModel
class ProviderSignInViewModel @Inject constructor(
    private val coordinator: ProviderOAuthCoordinator
) : ViewModel() {
    private val _launch = MutableStateFlow<ProviderSignInStart?>(null)
    val launch: StateFlow<ProviderSignInStart?> = _launch.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    fun start(clientType: ClientType, platformUid: String) {
        if (!clientType.supportsProviderSignIn()) return
        viewModelScope.launch {
            _message.value = null
            _accessToken.value = null
            val started = runCatching { coordinator.begin(clientType) }
            val session = started.getOrNull()
            if (session == null) {
                _message.value = started.exceptionOrNull()?.message ?: "Sign-in could not start"
                return@launch
            }
            _launch.value = session
            val token = runCatching { coordinator.awaitAccessToken(platformUid) }
            _launch.value = null
            token.onSuccess { _accessToken.value = it }
            token.onFailure { error -> _message.value = error.message ?: "Sign-in failed" }
        }
    }

    fun submitCode(platformUid: String, code: String) {
        viewModelScope.launch {
            val token = runCatching { coordinator.exchangePastedCode(platformUid, code) }
            token.onSuccess { _accessToken.value = it }
            token.onFailure { error -> _message.value = error.message ?: "That code was not accepted" }
        }
    }

    fun consumeLaunch() {
        _launch.value = null
    }

    override fun onCleared() {
        coordinator.cancel()
    }
}

@Composable
fun ProviderSignInControls(
    clientType: ClientType,
    platformUid: String,
    onAccessToken: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!clientType.supportsProviderSignIn()) return
    ProviderSignInForm(
        clientType = clientType,
        platformUid = platformUid,
        onAccessToken = onAccessToken,
        modifier = modifier
    )
}

@Composable
private fun ProviderSignInForm(
    clientType: ClientType,
    platformUid: String,
    onAccessToken: (String) -> Unit,
    modifier: Modifier = Modifier,
    signInViewModel: ProviderSignInViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val launch by signInViewModel.launch.collectAsStateWithLifecycle()
    val message by signInViewModel.message.collectAsStateWithLifecycle()
    val accessToken by signInViewModel.accessToken.collectAsStateWithLifecycle()
    var pastedCode by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(accessToken) {
        val token = accessToken ?: return@LaunchedEffect
        onAccessToken(token)
    }
    LaunchedEffect(launch) {
        val session = launch ?: return@LaunchedEffect
        launchProviderSignIn(context, session)
        signInViewModel.consumeLaunch()
    }
    Column(modifier = modifier.fillMaxWidth()) {
        TextButton(onClick = { signInViewModel.start(clientType, platformUid) }) {
            Text(stringResource(R.string.provider_sign_in))
        }
        Text(
            text = stringResource(signInDescription(clientType)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = pastedCode,
            onValueChange = { pastedCode = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            label = { Text(stringResource(R.string.provider_sign_in_code)) },
            singleLine = true
        )
        TextButton(
            onClick = { signInViewModel.submitCode(platformUid, pastedCode) },
            enabled = pastedCode.isNotBlank()
        ) {
            Text(stringResource(R.string.provider_sign_in_exchange))
        }
        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun signInDescription(clientType: ClientType): Int = when (clientType) {
    ClientType.XAI -> R.string.provider_sign_in_xai
    else -> R.string.provider_sign_in_openrouter
}

private fun launchProviderSignIn(context: Context, session: ProviderSignInStart) {
    val activity = context.findActivity() ?: return
    val service = AuthorizationService(activity)
    try {
        activity.startActivity(ProviderOAuthAppAuth.authorizationIntent(service, session))
    } finally {
        service.dispose()
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
