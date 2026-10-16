package dev.chungjungsoo.gptmobile.data.routine

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification tap target. The activity reads [pending] after startup and opens the chat.
 */
object RoutineChatLaunch {
    const val EXTRA_CHAT_ID = "dev.chungjungsoo.gptmobile.routine.CHAT_ID"
    const val EXTRA_ENABLED_PLATFORMS = "dev.chungjungsoo.gptmobile.routine.ENABLED_PLATFORMS"

    data class Pending(val chatId: Int, val enabledPlatforms: String)

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    fun offerFrom(intent: Intent?) {
        val chatId = intent?.getIntExtra(EXTRA_CHAT_ID, 0) ?: return
        if (chatId <= 0) return
        _pending.value = Pending(
            chatId = chatId,
            enabledPlatforms = intent.getStringExtra(EXTRA_ENABLED_PLATFORMS).orEmpty()
        )
    }

    fun consume() {
        _pending.value = null
    }

    fun route(chatId: Int, enabledPlatforms: String): String = "chat_room/$chatId?enabled=$enabledPlatforms"
}
