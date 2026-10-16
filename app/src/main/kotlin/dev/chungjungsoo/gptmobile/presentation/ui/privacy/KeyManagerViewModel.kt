package dev.chungjungsoo.gptmobile.presentation.ui.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.ProviderAuthStyle
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.security.KeyProbeResult
import dev.chungjungsoo.gptmobile.data.security.KeyStoragePolicy
import dev.chungjungsoo.gptmobile.data.security.ProviderKeyProbe
import dev.chungjungsoo.gptmobile.data.security.SecretEntry
import dev.chungjungsoo.gptmobile.data.security.SecretType
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import dev.chungjungsoo.gptmobile.data.security.VaultSession
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KeyEditorState(
    val existingId: String? = null,
    val platformId: Int? = null,
    val label: String = "",
    val type: SecretType = SecretType.API_KEY,
    val endpoint: String = "",
    val secret: String = ""
)

data class KeyRowState(
    val id: String,
    val label: String,
    val type: SecretType,
    val masked: String,
    val endpoint: String,
    val platformId: Int?
)

data class KeyManagerUiState(
    val unlocked: Boolean = false,
    val rows: List<KeyRowState> = emptyList(),
    val editor: KeyEditorState? = null,
    val message: String? = null
)

@HiltViewModel
class KeyManagerViewModel @Inject constructor(
    private val secretVault: SecretVault,
    private val vaultSession: VaultSession,
    private val settingRepository: SettingRepository,
    private val keyProbe: ProviderKeyProbe
) : ViewModel() {
    private val _state = MutableStateFlow(KeyManagerUiState())
    val state = _state.asStateFlow()
    private var platforms: List<PlatformV2> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            platforms = settingRepository.fetchPlatformV2s()
            val entries = secretVault.entries().associateBy { it.id }
            val platformRows = platforms.mapNotNull { platform ->
                val secretRef = platform.secretRef ?: return@mapNotNull null
                val entry = entries[secretRef]
                KeyRowState(
                    id = secretRef,
                    label = platform.name,
                    type = SecretType.API_KEY,
                    masked = entry?.masked?.ifBlank { "••••" } ?: "••••",
                    endpoint = platform.apiUrl,
                    platformId = platform.id
                )
            }
            val used = platformRows.map { it.id }.toSet()
            val extra = entries.values.filter { it.id !in used }.map { entry ->
                KeyRowState(
                    id = entry.id,
                    label = entry.label,
                    type = entry.type,
                    masked = entry.masked.ifBlank { "••••" },
                    endpoint = entry.endpoint,
                    platformId = null
                )
            }
            _state.update {
                it.copy(
                    unlocked = vaultSession.isUnlocked(),
                    rows = platformRows + extra
                )
            }
        }
    }

    fun beginAdd() {
        _state.update { it.copy(editor = KeyEditorState(), message = null) }
    }

    fun beginRotate(row: KeyRowState) {
        _state.update {
            it.copy(
                editor = KeyEditorState(
                    existingId = row.id,
                    platformId = row.platformId,
                    label = row.label,
                    type = row.type,
                    endpoint = row.endpoint
                ),
                message = null
            )
        }
    }

    fun updateEditor(editor: KeyEditorState) {
        _state.update { it.copy(editor = editor) }
    }

    fun dismissEditor() {
        _state.update { it.copy(editor = null) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun save() {
        val editor = _state.value.editor ?: return
        val secret = editor.secret.trim()
        if (editor.label.isBlank() || secret.isEmpty()) {
            _state.update { it.copy(message = "Enter a name and a secret.") }
            return
        }
        viewModelScope.launch {
            val platform = platforms.firstOrNull { it.id == editor.platformId }
            if (platform != null) {
                val ref = KeyStoragePolicy.secretRefFor(platform.uid, platform.secretRef)
                val bytes = secret.encodeToByteArray()
                try {
                    secretVault.put(ref, bytes)
                } finally {
                    bytes.fill(0)
                }
                settingRepository.updatePlatformV2(
                    KeyStoragePolicy.vaultedPlatform(platform, ref, editor.label.trim())
                )
            } else {
                val id = editor.existingId ?: "custom_${UUID.randomUUID().toString().replace("-", "")}"
                val bytes = secret.encodeToByteArray()
                try {
                    secretVault.store(
                        SecretEntry(
                            id = id,
                            type = editor.type,
                            label = editor.label.trim(),
                            endpoint = editor.endpoint.trim()
                        ),
                        bytes
                    )
                } finally {
                    bytes.fill(0)
                }
            }
            _state.update { it.copy(editor = null, message = "Saved.") }
            refresh()
        }
    }

    fun delete(row: KeyRowState) {
        viewModelScope.launch {
            val platform = platforms.firstOrNull { it.id == row.platformId }
            if (platform != null) {
                platform.secretRef?.let { secretVault.delete(it) }
                settingRepository.updatePlatformV2(platform.copy(token = null, secretRef = null))
            } else {
                secretVault.delete(row.id)
            }
            _state.update { it.copy(message = "Deleted.") }
            refresh()
        }
    }

    fun test(row: KeyRowState) {
        viewModelScope.launch {
            if (!vaultSession.isUnlocked()) {
                _state.update { it.copy(unlocked = false, message = "Unlock the vault first.") }
                return@launch
            }
            val platform = platforms.firstOrNull { it.id == row.platformId }
            val style = when (platform?.compatibleType) {
                ClientType.GOOGLE -> ProviderAuthStyle.X_GOOG_API_KEY
                ClientType.ANTHROPIC -> ProviderAuthStyle.X_API_KEY
                else -> ProviderAuthStyle.BEARER
            }
            val endpoint = row.endpoint.ifBlank { platform?.apiUrl.orEmpty() }
            if (endpoint.isBlank()) {
                _state.update { it.copy(message = "Add an API URL before testing this key.") }
                return@launch
            }
            val result = keyProbe.test(endpoint, row.id, style)
            val message = when (result) {
                KeyProbeResult.Accepted -> "Key accepted."
                KeyProbeResult.Rejected -> "Key rejected."
                KeyProbeResult.Locked -> "Unlock the vault first."
                KeyProbeResult.Unreachable -> "Could not reach the provider."
            }
            _state.update { it.copy(unlocked = vaultSession.isUnlocked(), message = message) }
        }
    }

    fun onUnlocked() {
        vaultSession.unlock()
        refresh()
    }
}
