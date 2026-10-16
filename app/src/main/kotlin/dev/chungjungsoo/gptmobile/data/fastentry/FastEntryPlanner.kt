package dev.chungjungsoo.gptmobile.data.fastentry

/**
 * Routing and shortcut rules for fast entry.
 *
 * Voice opens [VOICE_SESSION_ROUTE] when that destination is already in the nav graph
 * (the voice stream's route, also the target the home-screen widget looks up).
 * If the graph instead uses the voice stream's chat-room template (`{openVoice}`),
 * that template is filled. This module does not register a second voice screen
 * and does not add a home-screen widget.
 */
object FastEntryPlanner {
    const val VOICE_SESSION_ROUTE = "voice_session"
    const val EXTRA_OPEN_VOICE = "dev.chungjungsoo.gptmobile.OPEN_VOICE"
    const val ACTION_NEW_CHAT = "dev.chungjungsoo.gptmobile.action.NEW_CHAT"
    const val ACTION_VOICE = "dev.chungjungsoo.gptmobile.action.VOICE"
    const val ACTION_QUICK_ASK = "dev.chungjungsoo.gptmobile.action.QUICK_ASK"
    const val ACTION_OPEN_CHAT = "dev.chungjungsoo.gptmobile.action.OPEN_CHAT"
    const val ACTION_PROCESS_TEXT = "android.intent.action.PROCESS_TEXT"
    const val ACTION_ASSIST = "android.intent.action.ASSIST"
    const val ACTION_VOICE_COMMAND = "android.intent.action.VOICE_COMMAND"
    const val EXTRA_CHAT_ID = "dev.chungjungsoo.gptmobile.extra.CHAT_ID"
    const val EXTRA_PLATFORMS = "dev.chungjungsoo.gptmobile.extra.PLATFORMS"
    const val EXTRA_BOT_UID = "dev.chungjungsoo.gptmobile.extra.BOT_UID"

    const val STATIC_SHORTCUT_COUNT = 3
    const val RECENT_CHAT_LIMIT = 3
    const val FAVOURITE_BOT_LIMIT = 10
    const val SHORT_LABEL_MAX = 24
    const val LONG_LABEL_MAX = 48

    const val ID_RECENT_CHAT_PREFIX = "recent-chat-"
    const val ID_FAVOURITE_BOT_PREFIX = "favourite-bot-"

    enum class Action {
        NEW_CHAT,
        VOICE,
        QUICK_ASK,
        OPEN_CHAT,
        PROCESS_TEXT
    }

    data class Request(
        val action: Action,
        val chatId: Int = 0,
        val platforms: List<String> = emptyList(),
        val botUid: String? = null,
        val selection: String? = null,
        val selectionReadOnly: Boolean = false,
        val id: Long = 0
    )

    sealed interface Destination {
        data class Chat(val route: String) : Destination
        data class Voice(val route: String) : Destination
        data object QuickAsk : Destination
        data object VoiceUnavailable : Destination
        data object MissingPlatforms : Destination
        data object MissingChat : Destination
        data object Ignore : Destination
    }

    enum class ShortcutKind { CHAT, BOT }

    data class RecentChat(val id: Int, val title: String, val enabledPlatforms: List<String>)

    data class FavouriteBot(val uid: String, val name: String)

    data class DynamicShortcut(
        val id: String,
        val shortLabel: String,
        val longLabel: String,
        val rank: Int,
        val kind: ShortcutKind,
        val chatId: Int = 0,
        val platforms: List<String> = emptyList(),
        val botUid: String? = null
    )

    data class PlatformChoice(val uid: String, val model: String)

    enum class Phase { IDLE, SENDING, ANSWERED, FAILED }

    fun parse(
        action: String?,
        chatId: Int = 0,
        platforms: String? = null,
        botUid: String? = null,
        openVoice: Boolean = false,
        selection: String? = null,
        selectionReadOnly: Boolean = false
    ): Request? {
        val platformList = platforms.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val bot = botUid?.trim()?.takeIf { it.isNotEmpty() }
        val selected = selection?.takeIf { it.isNotBlank() }
        val resolved = when {
            action == ACTION_PROCESS_TEXT -> Action.PROCESS_TEXT
            action == ACTION_QUICK_ASK -> Action.QUICK_ASK
            action == ACTION_OPEN_CHAT -> Action.OPEN_CHAT
            action == ACTION_VOICE || action == ACTION_ASSIST || action == ACTION_VOICE_COMMAND || openVoice -> Action.VOICE
            action == ACTION_NEW_CHAT -> Action.NEW_CHAT
            else -> return null
        }
        return Request(
            action = resolved,
            chatId = chatId,
            platforms = platformList,
            botUid = bot,
            selection = selected,
            selectionReadOnly = selectionReadOnly
        )
    }

    fun resolve(
        request: Request,
        enabledPlatforms: List<String>,
        registeredRoutes: Set<String>,
        chatExists: Boolean = true
    ): Destination {
        val template = registeredRoutes.firstOrNull { it.startsWith("chat_room/") }
        return when (request.action) {
            Action.QUICK_ASK,
            Action.PROCESS_TEXT -> Destination.QuickAsk

            Action.VOICE -> voiceDestination(registeredRoutes, enabledPlatforms)

            Action.NEW_CHAT -> {
                if (template == null) return Destination.Ignore
                if (enabledPlatforms.isEmpty()) return Destination.MissingPlatforms
                Destination.Chat(fillChatRoute(template, chatId = 0, platforms = enabledPlatforms, openVoice = false))
            }

            Action.OPEN_CHAT -> {
                if (request.chatId <= 0) return Destination.Ignore
                if (!chatExists) return Destination.MissingChat
                if (template == null) return Destination.Ignore
                val platforms = request.platforms.ifEmpty { enabledPlatforms }
                if (platforms.isEmpty()) return Destination.MissingPlatforms
                Destination.Chat(fillChatRoute(template, request.chatId, platforms, openVoice = false))
            }
        }
    }

    fun voiceDestination(registeredRoutes: Set<String>, enabledPlatforms: List<String>): Destination {
        val session = registeredRoutes.firstOrNull { route ->
            route.substringBefore('?').substringBefore('/') == VOICE_SESSION_ROUTE && '{' !in route
        }
        if (session != null) return Destination.Voice(VOICE_SESSION_ROUTE)
        val template = registeredRoutes.firstOrNull { "{openVoice}" in it } ?: return Destination.VoiceUnavailable
        if (enabledPlatforms.isEmpty()) return Destination.MissingPlatforms
        return Destination.Voice(fillChatRoute(template, chatId = 0, platforms = enabledPlatforms, openVoice = true))
    }

    fun fillChatRoute(template: String, chatId: Int, platforms: List<String>, openVoice: Boolean): String = template
        .replace("{chatRoomId}", chatId.toString())
        .replace("{enabledPlatforms}", platforms.joinToString(","))
        .replace("{openVoice}", openVoice.toString())

    fun dynamicBudget(maxPerActivity: Int, staticCount: Int = STATIC_SHORTCUT_COUNT): Int =
        (maxPerActivity - staticCount).coerceAtLeast(0)

    fun planDynamicShortcuts(
        chats: List<RecentChat>,
        favouriteBots: List<FavouriteBot>,
        budget: Int,
        untitledChat: String
    ): List<DynamicShortcut> {
        if (budget <= 0) return emptyList()
        val chatPlans = chats.asSequence()
            .filter { it.id > 0 }
            .distinctBy { it.id }
            .take(RECENT_CHAT_LIMIT)
            .map { chat ->
                val label = clip(chat.title, untitledChat)
                DynamicShortcut(
                    id = ID_RECENT_CHAT_PREFIX + chat.id,
                    shortLabel = clip(label, fallback = untitledChat, max = SHORT_LABEL_MAX),
                    longLabel = clip(label, fallback = untitledChat, max = LONG_LABEL_MAX),
                    rank = 0,
                    kind = ShortcutKind.CHAT,
                    chatId = chat.id,
                    platforms = chat.enabledPlatforms
                )
            }
        val botPlans = favouriteBots.asSequence()
            .filter { it.uid.isNotBlank() && it.name.isNotBlank() }
            .distinctBy { it.uid }
            .take(FAVOURITE_BOT_LIMIT)
            .map { bot ->
                DynamicShortcut(
                    id = ID_FAVOURITE_BOT_PREFIX + bot.uid,
                    shortLabel = clip(bot.name, fallback = bot.name, max = SHORT_LABEL_MAX),
                    longLabel = clip(bot.name, fallback = bot.name, max = LONG_LABEL_MAX),
                    rank = 0,
                    kind = ShortcutKind.BOT,
                    botUid = bot.uid
                )
            }
        return (chatPlans + botPlans).take(budget).mapIndexed { index, shortcut ->
            shortcut.copy(rank = index)
        }.toList()
    }

    fun updateFavouriteUids(current: Set<String>, botUid: String, favourite: Boolean): Set<String> {
        if (botUid.isBlank()) return current
        return if (favourite) current + botUid else current - botUid
    }

    fun pickPlatform(enabled: List<PlatformChoice>, preferredUid: String?): PlatformChoice? {
        if (enabled.isEmpty()) return null
        return preferredUid?.let { uid -> enabled.firstOrNull { it.uid == uid } } ?: enabled.first()
    }

    fun modelFor(platform: PlatformChoice, overrideModel: String?): String =
        overrideModel?.trim()?.takeIf { it.isNotEmpty() } ?: platform.model

    fun phaseFor(runStatus: String?): Phase = when (runStatus) {
        null, "QUEUED", "RUNNING" -> Phase.SENDING
        "COMPLETED" -> Phase.ANSWERED
        else -> Phase.FAILED
    }

    fun preferAnswer(current: String, incoming: String): String = when {
        incoming.isEmpty() -> current
        incoming.length >= current.length -> incoming
        current.startsWith(incoming) -> current
        else -> incoming
    }

    private fun clip(value: String, fallback: String, max: Int = LONG_LABEL_MAX): String {
        val clean = value.replace('\n', ' ').trim().ifBlank { fallback.trim() }
        if (clean.isEmpty()) return fallback.trim().take(max)
        if (clean.length <= max) return clean
        if (max <= 3) return clean.take(max)
        return clean.take(max - 3).trimEnd() + "..."
    }
}
