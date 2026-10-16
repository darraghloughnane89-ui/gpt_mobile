package dev.chungjungsoo.gptmobile.data.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentPolicyTest {
    @Test
    fun unlockedHomeWidget_revealsChatContent() {
        assertTrue(
            WidgetContentPolicy.canRevealChatContent(
                deviceLocked = false,
                onKeyguard = false,
                userAllowsLockedContent = false
            )
        )
    }

    @Test
    fun lockedOrKeyguardWidget_hidesChatContentUntilTheUserOptsIn() {
        assertFalse(
            WidgetContentPolicy.canRevealChatContent(
                deviceLocked = true,
                onKeyguard = false,
                userAllowsLockedContent = false
            )
        )
        assertFalse(
            WidgetContentPolicy.canRevealChatContent(
                deviceLocked = false,
                onKeyguard = true,
                userAllowsLockedContent = false
            )
        )
        assertTrue(
            WidgetContentPolicy.canRevealChatContent(
                deviceLocked = true,
                onKeyguard = true,
                userAllowsLockedContent = true
            )
        )
    }
}
