package dev.chungjungsoo.gptmobile.presentation.benchmark

import dev.chungjungsoo.gptmobile.data.database.dao.ChatRoomV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.MessageV2Dao
import dev.chungjungsoo.gptmobile.data.database.dao.PlatformV2Dao
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BenchmarkLaunch(
    val chatId: Int,
    val platformUid: String,
    val scenario: String
)

fun parseBenchmarkScenario(enabled: Boolean, action: String?, scenario: String?): String? {
    if (!enabled || action != BenchmarkChatSeeder.ACTION) return null
    return scenario?.takeIf {
        it == BenchmarkChatSeeder.SCENARIO_SCROLL || it == BenchmarkChatSeeder.SCENARIO_STREAM
    }
}

class BenchmarkChatSeeder @Inject constructor(
    private val platformDao: PlatformV2Dao,
    private val chatRoomDao: ChatRoomV2Dao,
    private val messageDao: MessageV2Dao
) {
    private val streamScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var streamJob: Job? = null

    suspend fun prepare(scenario: String): BenchmarkLaunch? {
        val requested = parseBenchmarkScenario(
            enabled = BenchmarkBuild.IS_ENABLED,
            action = ACTION,
            scenario = scenario
        ) ?: return null
        val platform = ensurePlatform()
        val chatId = ensureChat(platform.uid, requested)
        return BenchmarkLaunch(chatId = chatId, platformUid = platform.uid, scenario = requested)
    }

    fun startStreaming(chatId: Int) {
        if (!BenchmarkBuild.IS_ENABLED) return
        streamJob?.cancel()
        streamJob = streamScope.launch {
            val assistant = messageDao.loadMessages(chatId).lastOrNull { it.platformType != null } ?: return@launch
            var text = ""
            STREAM_CHUNKS.forEach { chunk ->
                text += chunk
                messageDao.editMessages(assistant.copy(content = text))
                delay(STREAM_CHUNK_DELAY_MS)
            }
        }
    }

    private suspend fun ensurePlatform(): PlatformV2 {
        val existing = platformDao.getPlatforms().firstOrNull { it.name == PLATFORM_NAME }
        if (existing != null) return existing
        val created = PlatformV2(
            name = PLATFORM_NAME,
            compatibleType = ClientType.OPENAI,
            enabled = true,
            apiUrl = "https://benchmark.invalid/v1/",
            model = "benchmark"
        )
        val id = platformDao.addPlatform(created).toInt()
        return created.copy(id = id)
    }

    private suspend fun ensureChat(platformUid: String, scenario: String): Int {
        val title = "$CHAT_TITLE $scenario"
        val existing = chatRoomDao.getChatRooms().firstOrNull { it.title == title }
        if (existing != null && messageDao.loadMessages(existing.id).isNotEmpty()) {
            return existing.id
        }
        val chatId = existing?.id ?: chatRoomDao.addChatRoom(
            ChatRoomV2(
                title = title,
                enabledPlatform = listOf(platformUid)
            )
        ).toInt()
        val turns = if (scenario == SCENARIO_STREAM) STREAM_TURNS else SCROLL_TURNS
        repeat(turns) { index ->
            messageDao.addMessages(
                MessageV2(
                    chatId = chatId,
                    content = "Question ${index + 1}",
                    platformType = null
                )
            )
            val userId = messageDao.loadMessages(chatId).last { it.platformType == null }.id
            val initial = if (scenario == SCENARIO_STREAM && index == turns - 1) {
                " "
            } else {
                assistantBody(index)
            }
            messageDao.addMessages(
                MessageV2(
                    chatId = chatId,
                    content = initial,
                    platformType = ClientType.OPENAI.name,
                    linkedMessageId = userId
                )
            )
        }
        return chatId
    }

    private fun assistantBody(index: Int): String = buildString {
        append("Reply ")
        append(index + 1)
        append("\n\n")
        repeat(8) {
            append("The chat list should keep moving while this paragraph stays on screen. ")
        }
    }

    companion object {
        const val ACTION = "dev.chungjungsoo.gptmobile.benchmark.SCENARIO"
        const val EXTRA_SCENARIO = "scenario"
        const val SCENARIO_SCROLL = "scroll"
        const val SCENARIO_STREAM = "stream"
        private const val PLATFORM_NAME = "Benchmark"
        private const val CHAT_TITLE = "Benchmark"
        private const val SCROLL_TURNS = 24
        private const val STREAM_TURNS = 6
        private const val STREAM_CHUNK_DELAY_MS = 50L
        private val STREAM_CHUNKS = listOf(
            "The ", "reply ", "streams ", "one ", "token ", "at ", "a ", "time ",
            "so ", "the ", "list ", "can ", "follow ", "the ", "newest ", "line. "
        ).let { words -> List(5) { words }.flatten() }
    }
}
