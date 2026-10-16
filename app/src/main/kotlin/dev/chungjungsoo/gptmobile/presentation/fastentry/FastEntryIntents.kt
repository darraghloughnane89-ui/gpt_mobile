package dev.chungjungsoo.gptmobile.presentation.fastentry

import android.content.Context
import android.content.Intent
import dev.chungjungsoo.gptmobile.data.fastentry.FastEntryPlanner
import dev.chungjungsoo.gptmobile.presentation.ui.main.MainActivity

fun Intent.toFastEntryRequest(): FastEntryPlanner.Request? = FastEntryPlanner.parse(
    action = action,
    chatId = getIntExtra(FastEntryPlanner.EXTRA_CHAT_ID, 0),
    platforms = getStringExtra(FastEntryPlanner.EXTRA_PLATFORMS),
    botUid = getStringExtra(FastEntryPlanner.EXTRA_BOT_UID),
    openVoice = getBooleanExtra(FastEntryPlanner.EXTRA_OPEN_VOICE, false),
    selection = getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString(),
    selectionReadOnly = getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
)

object FastEntryIntents {
    fun newChat(context: Context, botUid: String? = null): Intent = mainIntent(context, FastEntryPlanner.ACTION_NEW_CHAT).apply {
        if (!botUid.isNullOrBlank()) putExtra(FastEntryPlanner.EXTRA_BOT_UID, botUid)
    }

    fun voice(context: Context): Intent = mainIntent(context, FastEntryPlanner.ACTION_VOICE).apply {
        putExtra(FastEntryPlanner.EXTRA_OPEN_VOICE, true)
    }

    fun openChat(context: Context, chatId: Int, platforms: List<String>): Intent =
        mainIntent(context, FastEntryPlanner.ACTION_OPEN_CHAT).apply {
            putExtra(FastEntryPlanner.EXTRA_CHAT_ID, chatId)
            putExtra(FastEntryPlanner.EXTRA_PLATFORMS, platforms.joinToString(","))
        }

    fun quickAsk(context: Context): Intent = Intent(FastEntryPlanner.ACTION_QUICK_ASK).apply {
        setClass(context, QuickAskActivity::class.java)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    private fun mainIntent(context: Context, action: String): Intent = Intent(action).apply {
        setClass(context, MainActivity::class.java)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
}
