package dev.chungjungsoo.gptmobile.data.voice

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import dev.chungjungsoo.gptmobile.data.security.SecretVault

class SecretVaultVoiceSpeechKeyStore(
    private val secretVault: SecretVault,
    private val platforms: suspend () -> List<PlatformV2>
) : VoiceSpeechKeyStore {
    override suspend fun hasKey(): Boolean = !readKey().isNullOrBlank()

    override suspend fun readKey(): String? {
        val dedicated = readSecret(MiMoSpeechModels.SECRET_REF)
        val candidates = platforms().map { platform ->
            MiMoKeyCandidate(
                enabled = platform.enabled,
                apiUrl = platform.apiUrl,
                token = platform.token
            )
        }
        return MiMoSpeechProtocol.selectKey(dedicated, candidates)
    }

    override suspend fun saveKey(key: String) {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "MiMo speech key is empty." }
        val bytes = trimmed.encodeToByteArray()
        try {
            secretVault.put(MiMoSpeechModels.SECRET_REF, bytes)
            val verified = secretVault.read(MiMoSpeechModels.SECRET_REF)
            try {
                check(verified != null && verified.contentEquals(bytes)) { "MiMo speech key could not be stored." }
            } finally {
                verified?.fill(0)
            }
        } finally {
            bytes.fill(0)
        }
    }

    override suspend fun clearKey() {
        secretVault.delete(MiMoSpeechModels.SECRET_REF)
    }

    override suspend fun preview(): String = MiMoSpeechProtocol.keyPreview(readKey().orEmpty())

    private suspend fun readSecret(secretRef: String): String? {
        val bytes = secretVault.read(secretRef) ?: return null
        return try {
            bytes.decodeToString().trim().takeIf { it.isNotEmpty() }
        } finally {
            bytes.fill(0)
        }
    }
}

class RepositoryVoiceSpeechKeyStore(
    secretVault: SecretVault,
    settingRepository: SettingRepository
) : VoiceSpeechKeyStore by SecretVaultVoiceSpeechKeyStore(
    secretVault = secretVault,
    platforms = { settingRepository.fetchPlatformV2s() }
)
