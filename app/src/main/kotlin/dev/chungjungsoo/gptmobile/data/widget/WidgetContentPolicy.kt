package dev.chungjungsoo.gptmobile.data.widget

/**
 * Chat titles, summaries, and anything that could carry a secret stay off the
 * widget while the device is locked, unless the user has opted in.
 * The opt-in never allows raw credentials; those are stripped separately.
 */
object WidgetContentPolicy {
    fun canRevealChatContent(
        deviceLocked: Boolean,
        onKeyguard: Boolean,
        userAllowsLockedContent: Boolean
    ): Boolean = userAllowsLockedContent || (!deviceLocked && !onKeyguard)
}
