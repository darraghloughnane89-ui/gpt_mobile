package dev.chungjungsoo.gptmobile.data.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneDeepLinkRouterTest {
    @Test
    fun resolve_newChat_usesEnabledPlatforms() {
        val destination = PhoneDeepLinkRouter.resolve(
            parts = PhoneLinks.parts("dev.chungjungsoo.gptmobile", "phone", "/new-chat", null),
            enabledPlatforms = listOf("platform-a"),
            voiceRouteRegistered = false
        )

        assertEquals(
            PhoneDeepLinkRouter.Destination.NewChat(listOf("platform-a"), launchCamera = false),
            destination
        )
    }

    @Test
    fun resolve_askText_opensComposer() {
        val destination = PhoneDeepLinkRouter.resolve(
            parts = PhoneLinks.parts("dev.chungjungsoo.gptmobile", "phone", "/ask", "input=text"),
            enabledPlatforms = listOf("platform-a"),
            voiceRouteRegistered = false
        )

        assertEquals(
            PhoneDeepLinkRouter.Destination.NewChat(
                listOf("platform-a"),
                launchCamera = false,
                composerInput = PhoneDeepLinkRouter.Destination.ComposerInput.TEXT
            ),
            destination
        )
    }

    @Test
    fun resolve_askMic_requestsSpeechInTheComposer() {
        val uri = PhoneLinks.ask(PhoneLinks.INPUT_MIC)
        val destination = PhoneDeepLinkRouter.resolve(
            parts = PhoneLinks.parts("dev.chungjungsoo.gptmobile", "phone", "/ask", uri.substringAfter('?')),
            enabledPlatforms = listOf("platform-a"),
            voiceRouteRegistered = false
        )

        assertEquals(
            PhoneDeepLinkRouter.Destination.NewChat(
                listOf("platform-a"),
                launchCamera = false,
                composerInput = PhoneDeepLinkRouter.Destination.ComposerInput.MIC
            ),
            destination
        )
    }

    @Test
    fun resolve_camera_requestsCaptureWhenAPlatformExists() {
        val destination = PhoneDeepLinkRouter.resolve(
            parts = PhoneLinks.parts("dev.chungjungsoo.gptmobile", "phone", "/camera", null),
            enabledPlatforms = listOf("platform-a", "platform-b"),
            voiceRouteRegistered = false
        )

        assertEquals(
            PhoneDeepLinkRouter.Destination.NewChat(listOf("platform-a", "platform-b"), launchCamera = true),
            destination
        )
    }

    @Test
    fun resolve_cameraWithoutPlatforms_reportsMissingPlatforms() {
        val destination = PhoneDeepLinkRouter.resolve(
            parts = PhoneLinks.parts("dev.chungjungsoo.gptmobile", "phone", "/camera", null),
            enabledPlatforms = emptyList(),
            voiceRouteRegistered = false
        )

        assertEquals(PhoneDeepLinkRouter.Destination.MissingPlatforms, destination)
    }

    @Test
    fun resolve_voice_isUnavailableUntilTheRouteExists() {
        val parts = PhoneLinks.parts("dev.chungjungsoo.gptmobile", "phone", "/voice", null)

        assertEquals(
            PhoneDeepLinkRouter.Destination.VoiceUnavailable,
            PhoneDeepLinkRouter.resolve(parts, listOf("platform-a"), voiceRouteRegistered = false)
        )
        assertEquals(
            PhoneDeepLinkRouter.Destination.Voice,
            PhoneDeepLinkRouter.resolve(parts, listOf("platform-a"), voiceRouteRegistered = true)
        )
    }

    @Test
    fun resolve_chatLink_keepsEncodedPlatforms() {
        val uri = PhoneLinks.chat(12, listOf("uid-1", "uid-2"))
        val query = uri.substringAfter('?')
        val destination = PhoneDeepLinkRouter.resolve(
            parts = PhoneLinks.parts("dev.chungjungsoo.gptmobile", "phone", "/chat/12", query),
            enabledPlatforms = emptyList(),
            voiceRouteRegistered = false
        )

        assertEquals(
            PhoneDeepLinkRouter.Destination.OpenChat(12, listOf("uid-1", "uid-2")),
            destination
        )
    }

    @Test
    fun resolve_oauthLink_isIgnored() {
        val destination = PhoneDeepLinkRouter.resolve(
            parts = PhoneLinks.parts("dev.chungjungsoo.gptmobile", "oauth", "/mcp/callback", null),
            enabledPlatforms = listOf("platform-a"),
            voiceRouteRegistered = true
        )

        assertEquals(PhoneDeepLinkRouter.Destination.Ignore, destination)
    }
}
