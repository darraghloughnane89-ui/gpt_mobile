package dev.chungjungsoo.gptmobile.presentation.ui.hermes

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.hermes.HermesCredentialStore
import dev.chungjungsoo.gptmobile.data.hermes.HermesHandoff
import dev.chungjungsoo.gptmobile.data.hermes.HermesHandoffRequest
import dev.chungjungsoo.gptmobile.data.hermes.HermesHandoffResult
import dev.chungjungsoo.gptmobile.data.hermes.HermesPlatformSync
import dev.chungjungsoo.gptmobile.data.hermes.HermesProbeResult
import dev.chungjungsoo.gptmobile.data.hermes.HermesRole
import dev.chungjungsoo.gptmobile.data.hermes.HermesRouteMode
import dev.chungjungsoo.gptmobile.data.hermes.HermesSettingsStore
import dev.chungjungsoo.gptmobile.data.hermes.HermesThreadReducer
import dev.chungjungsoo.gptmobile.data.hermes.HermesThreadStore
import dev.chungjungsoo.gptmobile.data.hermes.HermesTransport
import dev.chungjungsoo.gptmobile.data.hermes.HermesWorkScheduler
import dev.chungjungsoo.gptmobile.data.hermes.isValidHermesProfile
import dev.chungjungsoo.gptmobile.data.hermes.parseHermesEndpoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HermesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: HermesSettingsStore,
    private val credentials: HermesCredentialStore,
    private val threads: HermesThreadStore,
    private val transport: HermesTransport,
    private val handoff: HermesHandoff,
    private val scheduler: HermesWorkScheduler,
    private val platformSync: HermesPlatformSync
) : ViewModel() {
    private val _form = MutableStateFlow(HermesSettingsUiState())
    val form = _form.asStateFlow()

    val threadUi = threads.snapshot.map { snapshot ->
        val thread = snapshot.threads.firstOrNull { it.id == snapshot.activeThreadId }
        HermesThreadUiState(
            title = thread?.title.orEmpty(),
            turns = thread?.turns.orEmpty().map { turn ->
                HermesTurnUi(
                    id = turn.id,
                    isUser = turn.role == HermesRole.USER,
                    text = turn.text,
                    status = if (turn.role == HermesRole.ASSISTANT) turn.status else null,
                    error = turn.error,
                    toolNotes = turn.toolNotes
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HermesThreadUiState())

    init {
        viewModelScope.launch { refreshForm() }
    }

    fun onEndpointChange(value: String) {
        _form.update { it.copy(endpoint = value, endpointError = null) }
    }

    fun onProfileChange(value: String) {
        _form.update { it.copy(profile = value, profileError = null) }
    }

    fun onTokenChange(value: String) {
        _form.update { it.copy(tokenDraft = value) }
    }

    fun onChatModelChange(enabled: Boolean) {
        _form.update { it.copy(chatModelEnabled = enabled) }
        viewModelScope.launch {
            if (!persist(resetRoute = false)) return@launch
            val settings = settingsStore.current().copy(chatModelEnabled = enabled)
            settingsStore.update { settings }
            platformSync.sync(settings, credentials.read())
        }
    }

    fun save() {
        viewModelScope.launch {
            _form.update { it.copy(saving = true) }
            val saved = persist(resetRoute = true)
            if (saved) {
                scheduler.enqueueDrain()
                platformSync.sync(settingsStore.current(), credentials.read())
            }
            _form.update { it.copy(saving = false) }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            if (!persist(resetRoute = true)) return@launch
            _form.update { it.copy(testing = true, testResult = null) }
            val settings = settingsStore.current()
            val token = credentials.read().orEmpty()
            val result = transport.probe(settings.endpoint, settings.profile, token)
            if (result is HermesProbeResult.Success) {
                settingsStore.update {
                    it.copy(routeMode = result.mode, modelId = result.modelId, streaming = result.streaming)
                }
                if (settings.chatModelEnabled) {
                    platformSync.sync(settingsStore.current(), token)
                }
            }
            _form.update { it.copy(testing = false, testResult = probeMessage(result)) }
        }
    }

    suspend fun handoff(request: HermesHandoffRequest): HermesHandoffResult = handoff.submit(request)

    fun retry(turnId: String) {
        viewModelScope.launch {
            threads.update { HermesThreadReducer.retry(it, turnId, UUID.randomUUID().toString()) }
            scheduler.enqueueDrain()
        }
    }

    fun newThread() {
        viewModelScope.launch {
            threads.update { HermesThreadReducer.startNewThread(it, UUID.randomUUID().toString()) }
        }
    }

    private suspend fun refreshForm() {
        val settings = settingsStore.current()
        _form.update {
            it.copy(
                endpoint = settings.endpoint,
                profile = settings.profile,
                chatModelEnabled = settings.chatModelEnabled,
                tokenSet = !credentials.read().isNullOrBlank(),
                tokenDraft = ""
            )
        }
    }

    private suspend fun persist(resetRoute: Boolean): Boolean {
        val form = _form.value
        val endpoint = form.endpoint.trim()
        val profile = form.profile.trim()
        val endpointError = if (endpoint.isNotEmpty() && parseHermesEndpoint(endpoint) == null) {
            context.getString(R.string.hermes_endpoint_invalid)
        } else {
            null
        }
        val profileError = if (!isValidHermesProfile(profile)) {
            context.getString(R.string.hermes_profile_invalid)
        } else {
            null
        }
        if (endpointError != null || profileError != null) {
            _form.update { it.copy(endpointError = endpointError, profileError = profileError) }
            return false
        }
        if (form.tokenDraft.isNotBlank()) {
            credentials.write(form.tokenDraft)
        }
        settingsStore.update { current ->
            val endpointChanged = current.endpoint != endpoint || current.profile != profile
            current.copy(
                endpoint = endpoint,
                profile = profile,
                chatModelEnabled = form.chatModelEnabled,
                routeMode = if (resetRoute && endpointChanged) HermesRouteMode.UNKNOWN else current.routeMode,
                modelId = if (resetRoute && endpointChanged) "" else current.modelId
            )
        }
        _form.update { it.copy(tokenDraft = "", tokenSet = !credentials.read().isNullOrBlank(), endpoint = endpoint, profile = profile) }
        return true
    }

    private fun probeMessage(result: HermesProbeResult): String = when (result) {
        is HermesProbeResult.Success -> {
            val route = if (result.mode == HermesRouteMode.MULTIPLEX) "profile prefix" else "direct"
            if (result.streaming) {
                context.getString(R.string.hermes_probe_ok_streaming, route, result.modelId)
            } else {
                context.getString(R.string.hermes_probe_ok, route, result.modelId)
            }
        }
        is HermesProbeResult.Unreachable -> context.getString(R.string.hermes_probe_unreachable, result.detail)
        HermesProbeResult.Unauthorized -> context.getString(R.string.hermes_probe_unauthorized)
        is HermesProbeResult.ProfileMismatch -> context.getString(R.string.hermes_probe_profile_mismatch, result.profile)
        HermesProbeResult.NeedsKey -> context.getString(R.string.hermes_probe_needs_key)
        is HermesProbeResult.Failed -> context.getString(R.string.hermes_probe_failed, result.detail)
    }
}
