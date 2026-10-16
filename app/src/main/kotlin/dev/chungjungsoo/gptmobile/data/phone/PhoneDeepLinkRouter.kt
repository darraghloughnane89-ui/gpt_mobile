package dev.chungjungsoo.gptmobile.data.phone

/**
 * Turns a phone deep link into a navigation outcome.
 * Voice is only opened when the voice stream has registered [PhoneLinks.VOICE_NAV_ROUTE].
 */
object PhoneDeepLinkRouter {
    sealed interface Destination {
        data class NewChat(
            val enabledPlatforms: List<String>,
            val launchCamera: Boolean
        ) : Destination

        data class OpenChat(
            val chatId: Int,
            val enabledPlatforms: List<String>
        ) : Destination

        data object Voice : Destination
        data object VoiceUnavailable : Destination
        data object MissingPlatforms : Destination
        data object Ignore : Destination
    }

    fun resolve(
        parts: PhoneLinks.Parts,
        enabledPlatforms: List<String>,
        voiceRouteRegistered: Boolean
    ): Destination {
        if (!PhoneLinks.isPhoneDeepLink(parts.scheme, parts.host)) return Destination.Ignore
        val path = parts.path.orEmpty().trimEnd('/')
        return when {
            path == PhoneLinks.PATH_NEW_CHAT -> newChat(enabledPlatforms, launchCamera = false)
            path == PhoneLinks.PATH_CAMERA -> newChat(enabledPlatforms, launchCamera = true)
            path == PhoneLinks.PATH_VOICE -> if (voiceRouteRegistered) Destination.Voice else Destination.VoiceUnavailable
            path.startsWith("${PhoneLinks.PATH_CHAT}/") -> {
                val chatId = parts.chatId ?: return Destination.Ignore
                val platforms = parts.platforms.ifEmpty { enabledPlatforms }
                if (platforms.isEmpty()) Destination.MissingPlatforms else Destination.OpenChat(chatId, platforms)
            }
            else -> Destination.Ignore
        }
    }

    private fun newChat(enabledPlatforms: List<String>, launchCamera: Boolean): Destination =
        if (enabledPlatforms.isEmpty()) {
            Destination.MissingPlatforms
        } else {
            Destination.NewChat(enabledPlatforms, launchCamera)
        }
}
