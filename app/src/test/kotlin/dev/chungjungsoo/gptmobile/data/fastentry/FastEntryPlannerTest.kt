package dev.chungjungsoo.gptmobile.data.fastentry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastEntryPlannerTest {
    private val chatTemplate = "chat_room/{chatRoomId}?enabled={enabledPlatforms}"
    private val voiceTemplate = "chat_room/{chatRoomId}?enabled={enabledPlatforms}&voice={openVoice}"

    @Test
    fun parse_recognizesStaticActionsVoiceExtraAndSelection() {
        assertEquals(
            FastEntryPlanner.Action.NEW_CHAT,
            FastEntryPlanner.parse(FastEntryPlanner.ACTION_NEW_CHAT, botUid = " bot ")?.action
        )
        assertEquals("bot", FastEntryPlanner.parse(FastEntryPlanner.ACTION_NEW_CHAT, botUid = " bot ")?.botUid)
        assertEquals(FastEntryPlanner.Action.VOICE, FastEntryPlanner.parse(FastEntryPlanner.ACTION_ASSIST)?.action)
        assertEquals(
            FastEntryPlanner.Action.VOICE,
            FastEntryPlanner.parse(action = "android.intent.action.MAIN", openVoice = true)?.action
        )
        val selection = FastEntryPlanner.parse(
            action = FastEntryPlanner.ACTION_PROCESS_TEXT,
            selection = "  explain this  ",
            selectionReadOnly = true
        )
        assertEquals(FastEntryPlanner.Action.PROCESS_TEXT, selection?.action)
        assertEquals("  explain this  ", selection?.selection)
        assertTrue(selection!!.selectionReadOnly)
        assertNull(FastEntryPlanner.parse("android.intent.action.MAIN"))
    }

    @Test
    fun resolve_newChatAndOpenChat_useEnabledPlatforms() {
        val routes = setOf(chatTemplate)
        val created = FastEntryPlanner.resolve(
            request = FastEntryPlanner.Request(FastEntryPlanner.Action.NEW_CHAT),
            enabledPlatforms = listOf("alpha", "beta"),
            registeredRoutes = routes
        )
        assertEquals(
            FastEntryPlanner.Destination.Chat("chat_room/0?enabled=alpha,beta"),
            created
        )
        val opened = FastEntryPlanner.resolve(
            request = FastEntryPlanner.Request(
                action = FastEntryPlanner.Action.OPEN_CHAT,
                chatId = 7,
                platforms = listOf("alpha")
            ),
            enabledPlatforms = listOf("beta"),
            registeredRoutes = routes
        )
        assertEquals(
            FastEntryPlanner.Destination.Chat("chat_room/7?enabled=alpha"),
            opened
        )
        assertEquals(
            FastEntryPlanner.Destination.MissingPlatforms,
            FastEntryPlanner.resolve(
                FastEntryPlanner.Request(FastEntryPlanner.Action.NEW_CHAT),
                emptyList(),
                routes
            )
        )
        assertEquals(
            FastEntryPlanner.Destination.MissingChat,
            FastEntryPlanner.resolve(
                FastEntryPlanner.Request(FastEntryPlanner.Action.OPEN_CHAT, chatId = 4),
                listOf("alpha"),
                routes,
                chatExists = false
            )
        )
    }

    @Test
    fun voiceDestination_prefersVoiceSessionThenOpenVoiceTemplate() {
        val session = FastEntryPlanner.voiceDestination(
            registeredRoutes = setOf(chatTemplate, FastEntryPlanner.VOICE_SESSION_ROUTE, voiceTemplate),
            enabledPlatforms = listOf("alpha")
        )
        assertEquals(FastEntryPlanner.Destination.Voice(FastEntryPlanner.VOICE_SESSION_ROUTE), session)

        val query = FastEntryPlanner.voiceDestination(
            registeredRoutes = setOf(voiceTemplate),
            enabledPlatforms = listOf("alpha", "beta")
        )
        assertEquals(
            FastEntryPlanner.Destination.Voice("chat_room/0?enabled=alpha,beta&voice=true"),
            query
        )
        assertEquals(
            FastEntryPlanner.Destination.VoiceUnavailable,
            FastEntryPlanner.voiceDestination(setOf(chatTemplate), listOf("alpha"))
        )
        assertEquals(
            FastEntryPlanner.Destination.MissingPlatforms,
            FastEntryPlanner.voiceDestination(setOf(voiceTemplate), emptyList())
        )
    }

    @Test
    fun voiceEntry_opensVoiceSessionAndNewChatKeepsProjectId() {
        val current = "chat_room/{chatRoomId}?enabled={enabledPlatforms}&projectId={projectId}&voice={openVoice}"
        val routes = setOf(current, FastEntryPlanner.VOICE_SESSION_ROUTE)
        assertEquals(
            FastEntryPlanner.Destination.Voice(FastEntryPlanner.VOICE_SESSION_ROUTE),
            FastEntryPlanner.resolve(
                request = FastEntryPlanner.Request(FastEntryPlanner.Action.VOICE),
                enabledPlatforms = listOf("alpha"),
                registeredRoutes = routes
            )
        )
        assertEquals(
            FastEntryPlanner.Destination.Chat("chat_room/0?enabled=alpha&projectId=-1&voice=false"),
            FastEntryPlanner.resolve(
                request = FastEntryPlanner.Request(FastEntryPlanner.Action.NEW_CHAT),
                enabledPlatforms = listOf("alpha"),
                registeredRoutes = routes
            )
        )
    }

    @Test
    fun planDynamicShortcuts_keepsLastThreeChatsAheadOfFavouriteBots() {
        val chats = (1..5).map { id ->
            FastEntryPlanner.RecentChat(id = id, title = "Chat $id", enabledPlatforms = listOf("p"))
        }
        val bots = listOf(
            FastEntryPlanner.FavouriteBot("b-long", "A very long favourite bot name that should clip"),
            FastEntryPlanner.FavouriteBot("b-2", "Zed")
        )
        val planned = FastEntryPlanner.planDynamicShortcuts(
            chats = chats,
            favouriteBots = bots,
            budget = 4,
            untitledChat = "Untitled chat"
        )
        assertEquals(listOf("recent-chat-1", "recent-chat-2", "recent-chat-3", "favourite-bot-b-long"), planned.map { it.id })
        assertEquals(listOf(0, 1, 2, 3), planned.map { it.rank })
        assertTrue(planned[3].shortLabel.length <= FastEntryPlanner.SHORT_LABEL_MAX)
        assertTrue(planned[3].shortLabel.endsWith("..."))
        assertEquals(
            emptyList<FastEntryPlanner.DynamicShortcut>(),
            FastEntryPlanner.planDynamicShortcuts(chats, bots, budget = 0, untitledChat = "Untitled chat")
        )
        assertEquals(12, FastEntryPlanner.dynamicBudget(15))
        val blank = FastEntryPlanner.planDynamicShortcuts(
            chats = listOf(FastEntryPlanner.RecentChat(3, "  ", listOf("p"))),
            favouriteBots = emptyList(),
            budget = 2,
            untitledChat = "Untitled chat"
        )
        assertEquals("Untitled chat", blank.single().shortLabel)
    }

    @Test
    fun favouritesPlatformAndAnswer_followTheAskRules() {
        assertEquals(setOf("a", "b"), FastEntryPlanner.updateFavouriteUids(setOf("a"), "b", favourite = true))
        assertEquals(setOf("a"), FastEntryPlanner.updateFavouriteUids(setOf("a", "b"), "b", favourite = false))
        assertEquals(setOf("a"), FastEntryPlanner.updateFavouriteUids(setOf("a"), " ", favourite = true))

        val enabled = listOf(
            FastEntryPlanner.PlatformChoice("first", "model-a"),
            FastEntryPlanner.PlatformChoice("second", "model-b")
        )
        assertEquals("second", FastEntryPlanner.pickPlatform(enabled, "second")?.uid)
        assertEquals("first", FastEntryPlanner.pickPlatform(enabled, "missing")?.uid)
        assertNull(FastEntryPlanner.pickPlatform(emptyList(), "second"))
        assertEquals("override", FastEntryPlanner.modelFor(enabled[0], " override "))
        assertEquals("model-a", FastEntryPlanner.modelFor(enabled[0], " "))

        assertEquals(FastEntryPlanner.Phase.SENDING, FastEntryPlanner.phaseFor("RUNNING"))
        assertEquals(FastEntryPlanner.Phase.ANSWERED, FastEntryPlanner.phaseFor("COMPLETED"))
        assertEquals(FastEntryPlanner.Phase.FAILED, FastEntryPlanner.phaseFor("FAILED"))
        assertEquals("hello there", FastEntryPlanner.preferAnswer("hello", "hello there"))
        assertEquals("hello there", FastEntryPlanner.preferAnswer("hello there", "hello"))
        assertFalse(FastEntryPlanner.preferAnswer("kept", "").isEmpty())
    }
}
