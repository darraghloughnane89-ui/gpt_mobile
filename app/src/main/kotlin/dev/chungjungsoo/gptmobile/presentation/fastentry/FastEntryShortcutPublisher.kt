package dev.chungjungsoo.gptmobile.presentation.fastentry

import android.content.Context
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.bot.BotRepository
import dev.chungjungsoo.gptmobile.data.fastentry.FastEntryPlanner
import dev.chungjungsoo.gptmobile.data.repository.ChatRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class FastEntryShortcutPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val chatRepository: ChatRepository,
    private val botRepository: BotRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val publishMutex = Mutex()

    @Volatile
    private var lastSignature: String? = null

    fun publishAsync() {
        scope.launch {
            runCatching { publish() }
                .onFailure { error -> Log.w(TAG, "Dynamic shortcuts were not updated.", error) }
        }
    }

    suspend fun publish() {
        publishMutex.withLock {
            publishLocked()
        }
    }

    private suspend fun publishLocked() {
        val untitled = context.getString(R.string.shortcut_untitled_chat)
        val chats = chatRepository.fetchChatListV2().map { room ->
            FastEntryPlanner.RecentChat(
                id = room.id,
                title = room.title,
                enabledPlatforms = room.enabledPlatform
            )
        }
        val botsByUid = botRepository.listBots().associateBy { it.botUid }
        val favourites = botRepository.favouriteBotUids().mapNotNull { uid ->
            val bot = botsByUid[uid] ?: return@mapNotNull null
            FastEntryPlanner.FavouriteBot(uid = bot.botUid, name = bot.name)
        }.sortedBy { it.name.lowercase() }
        val budget = FastEntryPlanner.dynamicBudget(
            ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        )
        val plan = FastEntryPlanner.planDynamicShortcuts(
            chats = chats,
            favouriteBots = favourites,
            budget = budget,
            untitledChat = untitled
        )
        val signature = plan.joinToString("|") { shortcut ->
            listOf(shortcut.id, shortcut.shortLabel, shortcut.rank, shortcut.platforms.joinToString(","), shortcut.botUid.orEmpty())
                .joinToString(":")
        }
        if (signature == lastSignature) return
        val shortcuts = plan.map { shortcut -> toShortcut(shortcut) }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        lastSignature = signature
    }

    private fun toShortcut(shortcut: FastEntryPlanner.DynamicShortcut): ShortcutInfoCompat {
        val intent = when (shortcut.kind) {
            FastEntryPlanner.ShortcutKind.CHAT -> FastEntryIntents.openChat(context, shortcut.chatId, shortcut.platforms)
            FastEntryPlanner.ShortcutKind.BOT -> FastEntryIntents.newChat(context, shortcut.botUid)
        }
        val icon = when (shortcut.kind) {
            FastEntryPlanner.ShortcutKind.CHAT -> R.drawable.ic_shortcut_chat
            FastEntryPlanner.ShortcutKind.BOT -> R.drawable.ic_shortcut_bot
        }
        return ShortcutInfoCompat.Builder(context, shortcut.id)
            .setShortLabel(shortcut.shortLabel)
            .setLongLabel(shortcut.longLabel)
            .setRank(shortcut.rank)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(intent)
            .build()
    }

    private companion object {
        const val TAG = "FastEntryShortcuts"
    }
}
