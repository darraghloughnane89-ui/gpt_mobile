package dev.chungjungsoo.gptmobile.presentation.ui.hermes

import dev.chungjungsoo.gptmobile.data.hermes.HermesDefaults
import dev.chungjungsoo.gptmobile.data.hermes.HermesDeliveryStatus

data class HermesSettingsUiState(
    val endpoint: String = "",
    val profile: String = HermesDefaults.PROFILE,
    val tokenDraft: String = "",
    val tokenSet: Boolean = false,
    val chatModelEnabled: Boolean = false,
    val testing: Boolean = false,
    val saving: Boolean = false,
    val testResult: String? = null,
    val endpointError: String? = null,
    val profileError: String? = null
)

data class HermesTurnUi(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val status: HermesDeliveryStatus? = null,
    val error: String? = null,
    val toolNotes: List<String> = emptyList()
)

data class HermesThreadUiState(
    val title: String = "",
    val turns: List<HermesTurnUi> = emptyList()
)
