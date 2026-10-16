package dev.chungjungsoo.gptmobile.presentation.ui.phonecontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.RobinVaultMeta
import dev.chungjungsoo.gptmobile.data.phonecontrol.PasskeyBridge
import dev.chungjungsoo.gptmobile.data.phonecontrol.PhoneControlEngine
import dev.chungjungsoo.gptmobile.data.phonecontrol.PhoneControlModelPlanner
import dev.chungjungsoo.gptmobile.data.phonecontrol.PhoneControlRepository
import dev.chungjungsoo.gptmobile.data.phonecontrol.PhoneObservation
import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.ActionConfirmation
import dev.chungjungsoo.gptmobile.data.phonecontrol.plan.PhoneAgentLoop
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.SafetyContext
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.SafetyGate
import dev.chungjungsoo.gptmobile.data.phonecontrol.safety.SafetyResult
import dev.chungjungsoo.gptmobile.data.phonecontrol.vault.RobinVaultStore
import dev.chungjungsoo.gptmobile.data.phonecontrol.vault.VaultSyncPolicy
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingPhoneConfirmation(
    val result: SafetyResult,
    val deferred: CompletableDeferred<ActionConfirmation>
)

@HiltViewModel
class PhoneControlViewModel @Inject constructor(
    private val repository: PhoneControlRepository,
    private val engine: PhoneControlEngine,
    private val planner: PhoneControlModelPlanner,
    private val vaultStore: RobinVaultStore
) : ViewModel() {
    private val _status = MutableStateFlow("Ready when you are.")
    val status = _status.asStateFlow()

    private val _pending = MutableStateFlow<PendingPhoneConfirmation?>(null)
    val pending = _pending.asStateFlow()

    private val _vaultTitles = MutableStateFlow("")
    val vaultTitles = _vaultTitles.asStateFlow()

    private val passkeys = PasskeyBridge()
    private var routineApproved = false

    init {
        engine.setStopListener { confirmPending(false, false) }
    }

    fun accessibilityEnabled(): Boolean = engine.isAttached()

    fun refreshVault() {
        viewModelScope.launch {
            _vaultTitles.value = runCatching { vaultStore.listForModel() }.getOrDefault("Vault is locked. Unlock with biometric to continue.")
        }
    }

    fun setRoutineApproved(approved: Boolean) {
        routineApproved = approved
        viewModelScope.launch { repository.setRoutine("Phone control", approved) }
    }

    fun start(chatRoomId: Int, goal: String, screenWidth: Int, screenHeight: Int) {
        if (!engine.isAttached()) {
            _status.value = "Phone control stopped. Check the allowlist or the stop overlay."
            return
        }
        viewModelScope.launch {
            if (!engine.begin()) {
                _status.value = "Phone control stopped. The stop overlay is not available."
                return@launch
            }
            val allowlist = repository.allowedPackages()
            val loop = PhoneAgentLoop(SafetyGate())
            var latest = emptyObservation()
            val result = loop.run(
                goal = goal,
                observe = {
                    latest = engine.observe(screenWidth, screenHeight, visionRequested = true) ?: latest
                    latest
                },
                plan = { currentGoal, observation, history ->
                    val plan = planner.plan(chatRoomId, currentGoal, observation, history + listOf(engine.notificationsForModel()))
                    if (plan.error != null) _status.value = plan.error
                    plan.actions
                },
                act = { action ->
                    val changed = engine.perform(action, latest.elements)
                    repository.log(latest.packageName, action.kind.name, action.summary(), if (changed) "acted" else "no change", confirmed = true)
                    changed
                },
                confirm = { safety ->
                    val deferred = CompletableDeferred<ActionConfirmation>()
                    _pending.value = PendingPhoneConfirmation(safety, deferred)
                    _status.value = safety.reason
                    deferred.await().also { _pending.value = null }
                },
                onSuspicious = { engine.emphasizeStop() },
                shouldStop = { engine.stopped.value },
                contextFor = { observation ->
                    SafetyContext(
                        foregroundPackage = observation.packageName,
                        appLabel = observation.appLabel,
                        allowlist = allowlist,
                        secureWindow = observation.secureWindow,
                        screenText = observation.treeText,
                        routineApproved = routineApproved,
                        background = false,
                        passwordFieldFocused = observation.passwordFieldVisible,
                        injectionDetected = observation.injectionDetected
                    )
                }
            )
            _status.value = result.message
            if (result.stopped) engine.stop()
        }
    }

    fun confirmPending(accepted: Boolean, biometric: Boolean) {
        _pending.value?.deferred?.complete(ActionConfirmation(accepted, biometric))
    }

    fun stop() {
        engine.stop()
        confirmPending(false, false)
        _status.value = "Phone control stopped. Check the allowlist or the stop overlay."
    }

    fun saveVault(title: String, username: String, secret: ByteArray, packageName: String, certSha256: String, domain: String) {
        if (VaultSyncPolicy.plaintextExportAllowed()) return
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            runCatching {
                vaultStore.save(
                    RobinVaultMeta(
                        id = id,
                        secretRef = "robin-$id",
                        title = title,
                        username = username.ifBlank { null },
                        packageName = packageName.ifBlank { null },
                        certSha256 = certSha256.ifBlank { null },
                        webDomain = domain.ifBlank { null },
                        createdAt = System.currentTimeMillis()
                    ),
                    secret
                )
                secret.fill(0)
                _status.value = "Saved in the vault. The model cannot read it."
                refreshVault()
            }.onFailure {
                secret.fill(0)
                _status.value = it.message ?: "Vault is locked. Unlock with biometric to continue."
            }
        }
    }

    suspend fun createPasskey(context: android.content.Context, requestJson: String, userConfirmed: Boolean): String =
        passkeys.create(context, requestJson, userConfirmed).also { _status.value = it }

    private fun emptyObservation() = PhoneObservation(
        packageName = "",
        appLabel = "",
        treeText = "",
        elements = emptyList(),
        focusedText = "",
        secureWindow = false,
        passwordFieldVisible = false,
        hiddenInstructionDetected = false,
        screenshotAttached = false
    )
}
