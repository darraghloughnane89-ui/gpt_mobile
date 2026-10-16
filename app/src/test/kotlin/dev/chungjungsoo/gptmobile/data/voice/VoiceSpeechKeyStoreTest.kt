package dev.chungjungsoo.gptmobile.data.voice

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.security.SecretVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSpeechKeyStoreTest {
    @Test
    fun `dedicated vault key wins and preview hides the secret`() = runBlocking {
        val vault = MemorySecretVault()
        val store = SecretVaultVoiceSpeechKeyStore(vault) {
            listOf(platform("https://token-plan-ams.xiaomimimo.com/v1", "tp-platform-key"))
        }
        store.saveKey(" tp-dedicated-key ")
        assertEquals("tp-dedicated-key", store.readKey())
        assertEquals("-key", store.preview())
        assertTrue(store.hasKey())
        assertFalse(store.preview().contains("tp-dedicated"))
        store.clearKey()
        assertEquals("tp-platform-key", store.readKey())
    }

    @Test
    fun `a non matching platform url is ignored`() = runBlocking {
        val store = SecretVaultVoiceSpeechKeyStore(MemorySecretVault()) {
            listOf(platform("https://api.openai.com/v1", "sk-other"))
        }
        assertNull(store.readKey())
        assertFalse(store.hasKey())
    }
}

class MemoryVoicePreferences(
    initial: VoiceSpeechSettings = VoiceSpeechSettings()
) : VoiceSpeechPreferences {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<VoiceSpeechSettings> = state

    override suspend fun setUseMiMo(enabled: Boolean) {
        state.update { it.copy(useMiMo = enabled) }
    }

    override suspend fun setVoiceId(voiceId: String) {
        state.update { it.copy(voiceId = MiMoSpeechModels.knownVoice(voiceId)) }
    }
}

class MemoryVoiceKeyStore(
    var key: String? = null
) : VoiceSpeechKeyStore {
    override suspend fun hasKey(): Boolean = !readKey().isNullOrBlank()

    override suspend fun readKey(): String? = key?.trim()?.takeIf { it.isNotEmpty() }

    override suspend fun saveKey(key: String) {
        this.key = key.trim()
    }

    override suspend fun clearKey() {
        key = null
    }

    override suspend fun preview(): String = MiMoSpeechProtocol.keyPreview(readKey().orEmpty())
}

private class MemorySecretVault : SecretVault {
    private val values = mutableMapOf<String, ByteArray>()

    override suspend fun put(secretRef: String, secret: ByteArray) {
        values[secretRef] = secret.copyOf()
    }

    override suspend fun read(secretRef: String): ByteArray? = values[secretRef]?.copyOf()

    override suspend fun delete(secretRef: String) {
        values.remove(secretRef)
    }
}

private fun platform(apiUrl: String, token: String) = PlatformV2(
    name = "MiMo",
    compatibleType = ClientType.CUSTOM,
    enabled = true,
    apiUrl = apiUrl,
    token = token,
    model = "mimo-v2.5-tts"
)
