package dev.chungjungsoo.gptmobile.presentation.common

import dev.chungjungsoo.gptmobile.data.phone.PhoneDeepLinkRouter
import dev.chungjungsoo.gptmobile.data.phone.PhoneLinks
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSessionRouteTest {
    @Test
    fun phoneVoiceLinkOpensVoiceMode() {
        val parts = PhoneLinks.parts("dev.chungjungsoo.gptmobile", "phone", "/voice", null)

        assertEquals("voice_session", Route.VOICE_SESSION)
        assertEquals(Route.VOICE_SESSION, PhoneLinks.VOICE_NAV_ROUTE)
        assertEquals(
            PhoneDeepLinkRouter.Destination.Voice,
            PhoneDeepLinkRouter.resolve(parts, listOf("platform-a"), voiceRouteRegistered = true)
        )
        assertEquals(
            "chat_room/0?enabled=platform-a&projectId=-1&voice=true",
            voiceSessionChatRoute(listOf("platform-a"))
        )
    }
}
