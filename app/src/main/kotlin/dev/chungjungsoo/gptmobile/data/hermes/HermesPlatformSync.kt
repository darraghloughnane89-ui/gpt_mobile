package dev.chungjungsoo.gptmobile.data.hermes

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.repository.SettingRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HermesPlatformSync @Inject constructor(
    private val settingRepository: SettingRepository
) {
    suspend fun sync(settings: HermesSettings, token: String?) {
        val existing = settingRepository.fetchPlatformV2s().firstOrNull { it.uid == HermesDefaults.PLATFORM_UID }
        if (!settings.chatModelEnabled) {
            if (existing != null && existing.enabled) {
                settingRepository.updatePlatformV2(existing.copy(enabled = false, token = token ?: existing.token))
            }
            return
        }
        val parsed = parseHermesEndpoint(settings.endpoint) ?: return
        val profile = settings.profile.ifBlank { HermesDefaults.PROFILE }
        if (!isValidHermesProfile(profile)) return
        val root = when (settings.routeMode) {
            HermesRouteMode.MULTIPLEX -> multiplexApiRoot(parsed.base, profile)
            else -> directApiRoot(parsed.base)
        }
        val model = hermesModelId(profile, settings.modelId)
        val apiUrl = if (root.endsWith("/")) root else "$root/"
        val platform = (
            existing ?: PlatformV2(
                uid = HermesDefaults.PLATFORM_UID,
                name = HermesDefaults.PLATFORM_NAME,
                compatibleType = ClientType.CUSTOM,
                apiUrl = apiUrl,
                model = model
            )
            ).copy(
            name = HermesDefaults.PLATFORM_NAME,
            compatibleType = ClientType.CUSTOM,
            enabled = true,
            apiUrl = apiUrl,
            model = model,
            token = token ?: existing?.token,
            stream = true,
            timeout = HermesDefaults.PLATFORM_TIMEOUT_SECONDS,
            systemPrompt = null
        )
        if (existing == null) {
            settingRepository.addPlatformV2(platform)
        } else {
            settingRepository.updatePlatformV2(platform)
        }
    }
}
